package br.com.ecowatch.read_model_service.scripts;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;

public class ProcessarMtRs {

    private static final Map<String, String> UF_FILE_IDS = new LinkedHashMap<>();
    static {
        UF_FILE_IDS.put("MT", "1gNi97uzEFDyKgCk_DNMwHtqj88o5Hgyp");
        UF_FILE_IDS.put("RS", "1Qqi7JBptVcG4M3dR2sA7zlPsV2-9mKTr");
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public static void main(String[] args) throws Exception {
        System.out.println("=== Processando MT e RS para complementar o SEEG ===");

        File outputFile = new File("ecowatch-brasil/read-model-service/src/main/resources/data/seeg_municipios_5anos.csv");
        if (!outputFile.exists()) {
            outputFile = new File("read-model-service/src/main/resources/data/seeg_municipios_5anos.csv");
        }

        Map<String, Double> emissaoMap = new HashMap<>(100_000);

        for (Map.Entry<String, String> entry : UF_FILE_IDS.entrySet()) {
            String uf = entry.getKey();
            String fileId = entry.getValue();
            System.out.printf("Baixando e processando %s (ID: %s)...\n", uf, fileId);

            String url = "https://drive.usercontent.google.com/download?id=" + fileId + "&export=download&confirm=t";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofMinutes(3))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() == 200) {
                processarStreamCsv(response.body(), uf, emissaoMap);
            } else {
                System.err.printf("HTTP %d para %s\n", response.statusCode(), uf);
            }
        }

        System.out.println("Gravando linhas adicionais no CSV...");
        long adicionadas = 0;
        try (BufferedWriter bw = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            List<String> keys = new ArrayList<>(emissaoMap.keySet());
            Collections.sort(keys);

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
                adicionadas++;
            }
        }

        System.out.printf("✅ Concluído! %d linhas adicionadas para MT e RS. Novo tamanho: %d bytes.\n", adicionadas, outputFile.length());
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
                // Parser manual rápido para evitar regex com lookahead
                List<String> parts = parseCsvLine(line);
                if (parts.size() <= colIbge) continue;

                String tipo = parts.get(colTipo).trim();
                if (tipo.contains("Remo") || tipo.contains("Bunker")) continue;

                String rawGas = parts.get(colGas).trim();
                String gas;
                if (rawGas.startsWith("CH4")) gas = "CH4";
                else if (rawGas.startsWith("CO2 (") || rawGas.equals("CO2")) gas = "CO2";
                else if (rawGas.startsWith("N2O")) gas = "N2O";
                else continue;

                String rawCidade = parts.get(colCidade).trim();
                if (rawCidade.isBlank() || rawCidade.startsWith("NA (") || "NA".equalsIgnoreCase(rawCidade)) continue;

                String ibge = parts.get(colIbge).trim();
                String uf = extrairUf(rawCidade, ufEsperada);
                String munNome = limparNomeMunicipio(rawCidade);
                ibge = sanitizarIbge(ibge, munNome, uf);

                String setor = normalizarSetor(parts.get(colSetor).trim());

                for (Map.Entry<Integer, Integer> yEntry : yearIndexMap.entrySet()) {
                    int colIdx = yEntry.getKey();
                    if (colIdx >= parts.size()) continue;

                    String valStr = parts.get(colIdx).trim();
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

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>(60);
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        result.add(sb.toString().trim());
        return result;
    }

    private static String sanitizarIbge(String ibge, String munNome, String uf) {
        if (ibge == null) ibge = "";
        ibge = ibge.replaceAll("[^0-9]", "");
        if ("Manaus".equalsIgnoreCase(munNome) && "AM".equalsIgnoreCase(uf)) {
            return "1302603";
        }
        if (ibge.length() > 7) {
            return ibge.substring(ibge.length() - 7);
        }
        return ibge;
    }

    private static String extrairUf(String rawCidade, String defaultUf) {
        int idx1 = rawCidade.lastIndexOf('(');
        int idx2 = rawCidade.lastIndexOf(')');
        if (idx1 != -1 && idx2 > idx1) {
            String uf = rawCidade.substring(idx1 + 1, idx2).trim();
            if (uf.length() == 2) return uf.toUpperCase();
        }
        return defaultUf;
    }

    private static String limparNomeMunicipio(String rawCidade) {
        int idx = rawCidade.lastIndexOf('(');
        if (idx != -1) {
            return rawCidade.substring(0, idx).trim();
        }
        return rawCidade.trim();
    }

    private static String normalizarSetor(String setor) {
        if (setor == null) return "Outros";
        if (setor.contains("Agropecu")) return "Agropecuária";
        if (setor.contains("Mudança") || setor.contains("Terra") || setor.contains("Florest")) return "Mudança de uso da terra";
        if (setor.contains("Energia")) return "Energia";
        if (setor.contains("Processos") || setor.contains("Industri")) return "Processos industriais";
        if (setor.contains("Resíduo")) return "Resíduos";
        return setor;
    }
}
