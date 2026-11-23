package br.com.ecowatch.read_model_service.scripts;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

public class CheckManausIbge {
    public static void main(String[] args) throws Exception {
        File file = new File("test_chunk.bin");
        List<String> sharedStrings = new ArrayList<>();
        try (ZipFile zip = new ZipFile(file)) {
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
                String currentCellRef = null;
                String cellType = null;
                StringBuilder cellVal = new StringBuilder();
                boolean insideV = false;
                Map<String, String> currentRow = new HashMap<>();

                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamReader.START_ELEMENT) {
                        String name = reader.getLocalName();
                        if ("row".equals(name)) {
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
                            String mun = currentRow.get("H");
                            if (mun != null && mun.toLowerCase().contains("manaus")) {
                                System.out.println("Encontrado Manaus: H=" + mun + ", I=" + currentRow.get("I") + ", G=" + currentRow.get("G") + ", raw=" + currentRow);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }
}
