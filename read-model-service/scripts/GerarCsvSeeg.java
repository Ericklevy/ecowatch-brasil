package br.com.ecowatch.read_model_service.scripts;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Script de pré-processamento de dados municipais do SEEG (Coleção 14.0).
 * Extrai os últimos 5 anos (2019-2023) agregados por município, UF, ano, gás e setor.
 * Gera um CSV enxuto para importação no banco relacional.
 */
public class GerarCsvSeeg {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Início do Processamento SEEG 14.0 ===");

        File xlsxFile = new File("test_chunk.bin");
        if (!xlsxFile.exists()) {
            xlsxFile = new File("../test_chunk.bin");
        }
        if (!xlsxFile.exists()) {
            System.err.println("Arquivo XLSX test_chunk.bin não encontrado na raiz!");
            return;
        }

        File paGasesCsv = new File("C:/Users/erick/.gemini/antigravity/brain/38cbf190-56bc-451b-aed8-ee3665e929fa/browser_sandbox/downloads/gases.csv");

        File outputDir = new File("ecowatch-brasil/read-model-service/src/main/resources/data");
        if (!outputDir.exists()) {
            outputDir = new File("read-model-service/src/main/resources/data");
        }
        outputDir.mkdirs();
        File outputFile = new File(outputDir, "seeg_municipios_5anos.csv");

        // Chave de agregação: ibge|mun|uf|ano|gas|setor -> emissao_t
        Map<String, Double> emissaoMap = new HashMap<>(200_000);

        // 1. Processa XLSX de CO2e (todos os 5.570 municípios do Brasil)
        System.out.println("Lendo planilha municipal de CO2e: " + xlsxFile.getAbsolutePath());
        processarXlsxCo2e(xlsxFile, emissaoMap);

        // 2. Processa gases.csv de Pará (PA) para CH4, CO2 e N2O reais
        if (paGasesCsv.exists()) {
            System.out.println("Lendo gases.csv de Pará: " + paGasesCsv.getAbsolutePath());
            processarGasesCsv(paGasesCsv, emissaoMap);
        } else {
            System.out.println("Aviso: gases.csv não encontrado em " + paGasesCsv.getAbsolutePath());
        }

        // 3. Exporta para CSV
        System.out.println("Gravando CSV consolidado: " + outputFile.getAbsolutePath());
        try (BufferedWriter bw = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
            bw.write("codigo_ibge,municipio,uf,ano,gas,setor,emissao_t");
            bw.newLine();

            List<String> keys = new ArrayList<>(emissaoMap.keySet());
            Collections.sort(keys);

            long exportedRows = 0;
            for (String key : keys) {
                double val = emissaoMap.get(key);
                if (val < 0.001) continue; // ignora valores zerados/negativos para manter banco enxuto

                // key format: ibge|mun|uf|ano|gas|setor
                String[] parts = key.split("\\|");
                bw.write(String.format(Locale.US, "%s,\"%s\",%s,%s,%s,\"%s\",%.2f",
                        parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], val));
                bw.newLine();
                exportedRows++;
            }
            System.out.println("✅ Concluído! Total de linhas geradas no CSV: " + exportedRows);
            System.out.println("Tamanho do arquivo: " + outputFile.length() + " bytes");
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
                        if (event == XMLStreamConstants.START_ELEMENT && "t".equals(reader.getLocalName())) {
                            insideT = true;
                            currentText.setLength(0);
                        } else if (event == XMLStreamConstants.CHARACTERS && insideT) {
                            currentText.append(reader.getText());
                        } else if (event == XMLStreamConstants.END_ELEMENT && "t".equals(reader.getLocalName())) {
                            insideT = false;
                        } else if (event == XMLStreamConstants.END_ELEMENT && "si".equals(reader.getLocalName())) {
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

                // Colunas dos anos no sheet3:
                // BG = 2019, BH = 2020, BI = 2021, BJ = 2022, BK = 2023
                Map<String, Integer> yearCols = Map.of(
                        "BG", 2019,
                        "BH", 2020,
                        "BI", 2021,
                        "BJ", 2022,
                        "BK", 2023
                );

                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
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
                    } else if (event == XMLStreamConstants.CHARACTERS && insideV) {
                        cellVal.append(reader.getText());
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
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
                                if (tipo != null && (tipo.contains("Remo") || tipo.contains("Bunker"))) {
                                    continue;
                                }

                                String rawMun = currentRow.get("H");
                                if (rawMun == null || rawMun.isBlank() || rawMun.startsWith("NA (")) {
                                    continue;
                                }

                                String ibge = currentRow.get("I");
                                if (ibge == null || ibge.isBlank()) continue;

                                String uf = extrairUf(rawMun, currentRow.get("G"));
                                String munNome = limparNomeMunicipio(rawMun);
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

    private static void processarGasesCsv(File csvFile, Map<String, Double> emissaoMap) throws Exception {
        try (BufferedReader br = Files.newBufferedReader(csvFile.toPath(), StandardCharsets.UTF_8)) {
            String headerLine = br.readLine();
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
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (parts.length <= colIbge) continue;

                String tipo = parts[colTipo].replace("\"", "").trim();
                if (!tipo.equalsIgnoreCase("Emissão")) continue;

                String rawGas = parts[colGas].replace("\"", "").trim();
                String gas = normalizarGas(rawGas);
                if (gas == null) continue; // processa apenas CH4, CO2, N2O

                String rawMun = parts[colCidade].replace("\"", "").trim();
                if (rawMun.isBlank() || rawMun.startsWith("NA (")) continue;

                String ibge = parts[colIbge].replace("\"", "").trim();
                String uf = extrairUf(rawMun, null);
                String munNome = limparNomeMunicipio(rawMun);
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
        }
    }

    private static String normalizarGas(String raw) {
        if (raw == null) return null;
        if (raw.startsWith("CH4")) return "CH4";
        if (raw.startsWith("CO2 (t)") || raw.equals("CO2")) return "CO2";
        if (raw.startsWith("N2O")) return "N2O";
        return null;
    }

    private static String normalizarSetor(String s) {
        if (s == null) return "Outros";
        String l = s.toLowerCase();
        if (l.contains("agropecu")) return "Agropecuária";
        if (l.contains("mudan") || l.contains("uso da terra") || l.contains("floresta")) return "Mudança de uso da terra";
        if (l.contains("energia")) return "Energia";
        if (l.contains("processos industriais") || l.contains("industrial")) return "Processos industriais";
        if (l.contains("resídu") || l.contains("residu")) return "Resíduos";
        return s;
    }

    private static String limparNomeMunicipio(String mun) {
        if (mun == null) return "";
        // Remove " (UF)" do final
        return mun.replaceAll("\\s*\\([A-Z]{2}\\)$", "").trim();
    }

    private static String extrairUf(String mun, String estadoNome) {
        if (mun != null && mun.matches(".*\\([A-Z]{2}\\)$")) {
            return mun.substring(mun.length() - 3, mun.length() - 1);
        }
        if (estadoNome != null) {
            return ESTADO_PARA_UF.getOrDefault(estadoNome.trim(), "BR");
        }
        return "BR";
    }

    private static final Map<String, String> ESTADO_PARA_UF = Map.ofEntries(
            Map.entry("Acre", "AC"), Map.entry("Alagoas", "AL"), Map.entry("Amapá", "AP"),
            Map.entry("Amazonas", "AM"), Map.entry("Bahia", "BA"), Map.entry("Ceará", "CE"),
            Map.entry("Distrito Federal", "DF"), Map.entry("Espírito Santo", "ES"), Map.entry("Goiás", "GO"),
            Map.entry("Maranhão", "MA"), Map.entry("Mato Grosso", "MT"), Map.entry("Mato Grosso do Sul", "MS"),
            Map.entry("Minas Gerais", "MG"), Map.entry("Pará", "PA"), Map.entry("Paraíba", "PB"),
            Map.entry("Paraná", "PR"), Map.entry("Pernambuco", "PE"), Map.entry("Piauí", "PI"),
            Map.entry("Rio de Janeiro", "RJ"), Map.entry("Rio Grande do Norte", "RN"), Map.entry("Rio Grande do Sul", "RS"),
            Map.entry("Rondônia", "RO"), Map.entry("Roraima", "RR"), Map.entry("Santa Catarina", "SC"),
            Map.entry("São Paulo", "SP"), Map.entry("Sergipe", "SE"), Map.entry("Tocantins", "TO")
    );
}
