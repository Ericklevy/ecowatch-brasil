package br.com.ecowatch.read_model_service.scripts;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

/**
 * Baixa e consolida os dados de emissões do SEEG (Coleção 14.0) para todos os estados brasileiros:
 * 1. CO2e para os 5.570 municípios do Brasil (via test_chunk.bin / planilha nacional resumida).
 * 2. CH4, CO2 e N2O para os 27 estados (via Google Drive gases.csv de cada UF).
 * 3. Corrige o código IBGE de Manaus (11302603 -> 1302603).
 * 4. Filtra linhas "NA" (emissões não alocadas).
 */
public class ProcessarTodosGasesSeeg {

    private static final Map<String, String> UF_FILE_IDS = new LinkedHashMap<>();

    static {
        UF_FILE_IDS.put("AC", "1F8KLS7JchmhOsNC8IyVu_Zxyx8lg27Bi");
        UF_FILE_IDS.put("AL", "1J8BsVWMHZ8cH1OvEP6HctRuuCtlgiohz");
        UF_FILE_IDS.put("AM", "1mQ-8UVapI9JbWegfUGCCrwU5tcXQt329");
        UF_FILE_IDS.put("AP", "1icGYU3a1jjpBxm-fALswknbtjoZjl2JB");
        UF_FILE_IDS.put("BA", "12tKnEFww829yDnzfqWUaR6zduOQvpP_x");
        UF_FILE_IDS.put("CE", "1QTnl0YBbX_rQl1MHKNT4kpgDDgsWczSb");
        UF_FILE_IDS.put("DF", "1zZtjPVlYtw24XGyW9JXZRkfTkjXZmVQe");
        UF_FILE_IDS.put("ES", "11hroLuVMK-ZaIC2D4Syoga21lRRGxvGE");
        UF_FILE_IDS.put("GO", "1KPG268noxri8NwPGiTb5ebioNzPobz4U");
        UF_FILE_IDS.put("MA", "1UjWkfhRFYNwSU0ti8Jnh0ftoZQJ5Vh6t");
        UF_FILE_IDS.put("MG", "1zTIAubcCjKmzHp3OzVFa3-7LXDevabZv");
        UF_FILE_IDS.put("MS", "1cJmLq9B7BZQ3cfBMBMJe_bR8wpX72FkA");
        UF_FILE_IDS.put("MT", "1r1_iNqYg8eW56qYp2Fq797O5K8i3v8fW");
        UF_FILE_IDS.put("PA", "1CRe44m6J9OIsgnKPbL5B4yt3pfCMUOep");
        UF_FILE_IDS.put("PB", "1qtwahYdfbveiwJzGMcogPeBIUylVFj5d");
        UF_FILE_IDS.put("PE", "1nXIHAoQvij03A-y_wGF-58X5nqeKAyrp");
        UF_FILE_IDS.put("PI", "1zxmIZDzx3sTQYnO3X6A8VYeYwKn6us7u");
        UF_FILE_IDS.put("PR", "1rGeQ6zLgJhXCLxzp7Qj3YSqBWwtixBtg");
        UF_FILE_IDS.put("RJ", "1Mq9x_B4TPmP3LA0ub0iUT19itudcem5b");
        UF_FILE_IDS.put("RN", "1QKNqif_o_McAIOnVWFRjpSOIQ8atOX5c");
        UF_FILE_IDS.put("RO", "1PMySbnq5FO-mcoQ4toucuUzUY2Hvlr1N");
        UF_FILE_IDS.put("RR", "1SjfHNa76gqtqrTKfGJMFj72yXj2ppQxL");
        UF_FILE_IDS.put("RS", "1JVY_1T6bGLbVBP7BvS8BrwCnJJZPfaEW");
        UF_FILE_IDS.put("SC", "1v2oidBnQIeb8XKO-DTiMlmtbvxs93l_y");
        UF_FILE_IDS.put("SE", "1QB2NZ6C-MqHwJiOrh6MgHwVDD9TNX2zg");
        UF_FILE_IDS.put("SP", "1bsCuOhMl0CbucTn8N4pQIAoCuG8ghuCT");
        UF_FILE_IDS.put("TO", "1lprj6Eq2LWq7661zBacooZkwn2zZYBlI");
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public static void main(String[] args) throws Exception {
        System.out.println("=== Iniciando Processamento Consolidado SEEG (Todos os Estados) ===");

        File xlsxFile = new File("test_chunk.bin");
        if (!xlsxFile.exists()) xlsxFile = new File("../test_chunk.bin");
        if (!xlsxFile.exists()) xlsxFile = new File("../../test_chunk.bin");

        File outputFile = new File("ecowatch-brasil/read-model-service/src/main/resources/data/seeg_municipios_5anos.csv");
        if (!outputFile.getParentFile().exists()) {
            outputFile = new File("read-model-service/src/main/resources/data/seeg_municipios_5anos.csv");
        }
        outputFile.getParentFile().mkdirs();

        // Chave: ibge|mun|uf|ano|gas|setor -> soma(emissao_t)
        Map<String, Double> emissaoMap = new HashMap<>(350_000);

        // 1. Processa XLSX de CO2e
        if (xlsxFile.exists()) {
            System.out.println("1/2 Processando planilha CO2e (todos os municípios): " + xlsxFile.getAbsolutePath());
            processarXlsxCo2e(xlsxFile, emissaoMap);
            System.out.printf("   Subtotal acumulado após CO2e: %d chaves\n", emissaoMap.size());
        } else {
            System.err.println("AVISO: test_chunk.bin não encontrado para CO2e!");
        }

        // 2. Processa gases.csv de cada um dos 27 estados
        System.out.println("2/2 Baixando e processando gases.csv para os 27 estados brasileiros...");
        int countUf = 0;
        for (Map.Entry<String, String> entry : UF_FILE_IDS.entrySet()) {
            String uf = entry.getKey();
            String fileId = entry.getValue();
            countUf++;
            System.out.printf("[%d/27] Processando %s (Google Drive ID: %s)...\n", countUf, uf, fileId);

            // Verifica se arquivo local já existe no sandbox
            File localCsv = null;
            if ("PA".equals(uf)) {
                File paCandidate = new File("C:/Users/erick/.gemini/antigravity/brain/38cbf190-56bc-451b-aed8-ee3665e929fa/browser_sandbox/downloads/gases.csv");
                if (paCandidate.exists()) localCsv = paCandidate;
            }

            try {
                if (localCsv != null) {
                    processarStreamCsv(new FileInputStream(localCsv), uf, emissaoMap);
                } else {
                    baixarEProcessarCsv(fileId, uf, emissaoMap);
                }
            } catch (Exception ex) {
                System.err.printf("   Erro ao processar estado %s: %s\n", uf, ex.getMessage());
            }
        }

        // 3. Exporta CSV final
        System.out.println("Gravando CSV final: " + outputFile.getAbsolutePath());
        try (BufferedWriter bw = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
            bw.write("codigo_ibge,municipio,uf,ano,gas,setor,emissao_t");
            bw.newLine();

            List<String> keys = new ArrayList<>(emissaoMap.keySet());
            Collections.sort(keys);

            long exportedRows = 0;
            for (String key : keys) {
                double val = emissaoMap.get(key);
                if (val < 0.01) continue;

                String[] parts = key.split("\\|");
                if (parts.length < 6) continue;

                String ibge = parts[0];
                String mun = parts[1];
                String uf = parts[2];
                String ano = parts[3];
                String gas = parts[4];
                String setor = parts[5];

                if ("NA".equalsIgnoreCase(uf) || "NA".equalsIgnoreCase(mun)) continue;

                bw.write(String.format(Locale.US, "%s,\"%s\",%s,%s,%s,\"%s\",%.2f",
                        ibge, mun, uf, ano, gas, setor, val));
                bw.newLine();
                exportedRows++;
            }

            System.out.println("✅ Processamento concluído com sucesso!");
            System.out.println("Total de linhas geradas no CSV: " + exportedRows);
            System.out.println("Tamanho do arquivo: " + outputFile.length() + " bytes");
        }
    }

    private static void baixarEProcessarCsv(String fileId, String ufEsperada, Map<String, Double> emissaoMap) throws Exception {
        String url = "https://drive.usercontent.google.com/download?id=" + fileId + "&export=download&confirm=t";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() == 200) {
            processarStreamCsv(response.body(), ufEsperada, emissaoMap);
        } else {
            System.err.printf("   HTTP %d para UF %s\n", response.statusCode(), ufEsperada);
        }
    }

    private static void processarStreamCsv(InputStream is, String ufEsperada, Map<String, Double> emissaoMap) throws Exception {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String headerLine = br.readLine();
            if (headerLine == null) return;

            String[] headers = headerLine.split(",");
            int colSetor = 0;
            int colTipo = 8;
            int colGas = 9;
            int colCidade = 10;
            int colIbge = 11;

            Map<Integer, Integer> yearIndexMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].replace("\"", "").trim();
                if (List.of("2019", "2020", "2021", "2022", "2023").contains(h)) {
                    yearIndexMap.put(i, Integer.parseInt(h));
                }
            }

            String line;
            long linhasLidas = 0;
            while ((line = br.readLine()) != null) {
                linhasLidas++;
                String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (parts.length <= colIbge) continue;

                String tipo = parts[colTipo].replace("\"", "").trim();
                if (tipo.contains("Remo") || tipo.contains("Bunker")) continue;

                String rawGas = parts[colGas].replace("\"", "").trim();
                String gas;
                if (rawGas.startsWith("CH4")) gas = "CH4";
                else if (rawGas.startsWith("CO2 (") || rawGas.equals("CO2")) gas = "CO2";
                else if (rawGas.startsWith("N2O")) gas = "N2O";
                else continue; // ignora gases secundários como COVNM, etc.

                String rawCidade = parts[colCidade].replace("\"", "").trim();
                if (rawCidade.isBlank() || rawCidade.startsWith("NA (") || "NA".equalsIgnoreCase(rawCidade)) continue;

                String ibge = parts[colIbge].replace("\"", "").trim();
                String uf = extrairUf(rawCidade, ufEsperada);
                String munNome = limparNomeMunicipio(rawCidade);
                ibge = sanitizarIbge(ibge, munNome, uf);

                String setor = normalizarSetor(parts[colSetor].replace("\"", "").trim());

                for (Map.Entry<Integer, Integer> yEntry : yearIndexMap.entrySet()) {
                    int colIdx = yEntry.getKey();
                    if (colIdx >= parts.length) continue;

                    String valStr = parts[colIdx].replace("\"", "").trim();
                    if (valStr.isBlank()) continue;

                    double val = 0.0;
                    try {
                        val = Double.parseDouble(valStr);
                    } catch (Exception ignored) {}

                    if (val > 0.001) {
                        String key = ibge + "|" + munNome + "|" + uf + "|" + yEntry.getValue() + "|" + gas + "|" + setor;
                        emissaoMap.put(key, emissaoMap.getOrDefault(key, 0.0) + val);
                    }
                }
            }
            System.out.printf("   %s: lidas %d linhas de dados.\n", ufEsperada, linhasLidas);
        }
    }

    private static void processarXlsxCo2e(File xlsxFile, Map<String, Double> emissaoMap) throws Exception {
        List<String> sharedStrings = new ArrayList<>();
        try (ZipFile zip = new ZipFile(xlsxFile)) {
            ZipEntry sst = zip.getEntry("xl/sharedStrings.xml");
            if (sst != null) {
                XMLInputFactory xif = XMLInputFactory.newFactory();
                try (InputStream is = zip.getInputStream(sst)) {
                    XMLStreamReader reader = xif.createXMLStreamReader(is);
                    StringBuilder currentText = new StringBuilder();
                    boolean insideT = false;
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamReader.START_ELEMENT && "t".equals(reader.getLocalName())) {
                            insideT = true;
                            currentText.setLength(0);
                        } else if (event == XMLStreamReader.CHARACTERS && insideT) {
                            currentText.append(reader.getText());
                        } else if (event == XMLStreamReader.END_ELEMENT && "t".equals(reader.getLocalName())) {
                            insideT = false;
                        } else if (event == XMLStreamReader.END_ELEMENT && "si".equals(reader.getLocalName())) {
                            sharedStrings.add(currentText.toString());
                        }
                    }
                }
            }

            ZipEntry sheetEntry = zip.getEntry("xl/worksheets/sheet3.xml");
            XMLInputFactory xif = XMLInputFactory.newFactory();
            try (InputStream is = zip.getInputStream(sheetEntry)) {
                XMLStreamReader reader = xif.createXMLStreamReader(is);
                int row = 0;
                String currentCellRef = null;
                String cellType = null;
                StringBuilder cellVal = new StringBuilder();
                boolean insideV = false;
                Map<String, String> currentRow = new HashMap<>();

                Map<String, Integer> yearCols = Map.of(
                        "BG", 2019,
                        "BH", 2020,
                        "BI", 2021,
                        "BJ", 2022,
                        "BK", 2023
                );

                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamReader.START_ELEMENT) {
                        String name = reader.getLocalName();
                        if ("row".equals(name)) {
                            row++;
                            currentRow.clear();
                        } else if ("c".equals(name)) {
                            currentCellRef = reader.getAttributeValue(null, "r");
                            cellType = reader.getAttributeValue(null, "t");
                            cellVal.setLength(0);
                        } else if ("v".equals(name)) {
                            insideV = true;
                        }
                    } else if (event == XMLStreamReader.CHARACTERS && insideV) {
                        cellVal.append(reader.getText());
                    } else if (event == XMLStreamReader.END_ELEMENT) {
                        String name = reader.getLocalName();
                        if ("v".equals(name)) {
                            insideV = false;
                            String val = cellVal.toString();
                            if ("s".equals(cellType)) {
                                int idx = Integer.parseInt(val);
                                val = (idx >= 0 && idx < sharedStrings.size()) ? sharedStrings.get(idx) : val;
                            }
                            String col = currentCellRef.replaceAll("[0-9]", "");
                            currentRow.put(col, val);
                        } else if ("row".equals(name)) {
                            if (row > 1) {
                                String tipo = currentRow.get("A");
                                if (tipo != null && (tipo.contains("Remo") || tipo.contains("Bunker"))) continue;

                                String rawMun = currentRow.get("H");
                                if (rawMun == null || rawMun.isBlank() || rawMun.startsWith("NA (") || "NA".equalsIgnoreCase(rawMun)) continue;

                                String rawIbge = currentRow.get("I");
                                if (rawIbge == null || rawIbge.isBlank()) continue;

                                String uf = extrairUf(rawMun, currentRow.get("G"));
                                if ("NA".equalsIgnoreCase(uf)) continue;

                                String munNome = limparNomeMunicipio(rawMun);
                                String ibge = sanitizarIbge(rawIbge, munNome, uf);
                                String setor = normalizarSetor(currentRow.get("B"));

                                for (Map.Entry<String, Integer> yEntry : yearCols.entrySet()) {
                                    String valStr = currentRow.get(yEntry.getKey());
                                    if (valStr == null || valStr.isBlank()) continue;
                                    double val = 0.0;
                                    try {
                                        val = Double.parseDouble(valStr);
                                    } catch (Exception ignored) {}

                                    if (val > 0.001) {
                                        String key = ibge + "|" + munNome + "|" + uf + "|" + yEntry.getValue() + "|CO2e|" + setor;
                                        emissaoMap.put(key, emissaoMap.getOrDefault(key, 0.0) + val);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static String sanitizarIbge(String rawIbge, String munNome, String uf) {
        if (rawIbge == null) return "";
        String limpo = rawIbge.replaceAll("[^0-9]", "").trim();
        if ("11302603".equals(limpo) || ("Manaus".equalsIgnoreCase(munNome) && "AM".equalsIgnoreCase(uf))) {
            return "1302603";
        }
        if (limpo.length() == 8 && limpo.startsWith("11") && "AM".equalsIgnoreCase(uf)) {
            return limpo.substring(1);
        }
        return limpo;
    }

    private static String extrairUf(String rawCidade, String fallbackUf) {
        if (rawCidade != null && rawCidade.contains("(") && rawCidade.contains(")")) {
            int ini = rawCidade.lastIndexOf('(');
            int fim = rawCidade.lastIndexOf(')');
            if (fim > ini) {
                String sub = rawCidade.substring(ini + 1, fim).trim().toUpperCase();
                if (sub.length() == 2) return sub;
            }
        }
        return (fallbackUf != null && !fallbackUf.isBlank()) ? fallbackUf.trim().toUpperCase() : "BR";
    }

    private static String limparNomeMunicipio(String rawCidade) {
        if (rawCidade == null) return "";
        int paren = rawCidade.lastIndexOf('(');
        if (paren > 0) {
            return rawCidade.substring(0, paren).trim();
        }
        return rawCidade.trim();
    }

    private static String normalizarSetor(String raw) {
        if (raw == null) return "Outros";
        String s = raw.toLowerCase();
        if (s.contains("agropecu")) return "Agropecuária";
        if (s.contains("mudança") || s.contains("floresta") || s.contains("uso da terra")) return "Mudança de uso da terra";
        if (s.contains("energia") || s.contains("combust")) return "Energia";
        if (s.contains("processo") || s.contains("industr")) return "Processos industriais";
        if (s.contains("resíduo") || s.contains("saneamento") || s.contains("efluente")) return "Resíduos";
        return raw.trim();
    }
}
