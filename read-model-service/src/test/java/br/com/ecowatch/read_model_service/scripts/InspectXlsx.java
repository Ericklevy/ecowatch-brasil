package br.com.ecowatch.read_model_service.scripts;

import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

public class InspectXlsx {
    public static void main(String[] args) throws Exception {
        File file = new File(args.length > 0 ? args[0] : "../../test_chunk.bin");
        System.out.println("Arquivo: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");

        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry wb = zip.getEntry("xl/workbook.xml");
            if (wb != null) {
                XMLInputFactory xif = XMLInputFactory.newFactory();
                try (InputStream is = zip.getInputStream(wb)) {
                    XMLStreamReader reader = xif.createXMLStreamReader(is);
                    while (reader.hasNext()) {
                        int event = reader.next();
                        if (event == XMLStreamReader.START_ELEMENT && "sheet".equals(reader.getLocalName())) {
                            System.out.println("Sheet: name=" + reader.getAttributeValue(null, "name") + 
                                               ", sheetId=" + reader.getAttributeValue(null, "sheetId") + 
                                               ", r:id=" + reader.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"));
                        }
                    }
                }
            }
        }
    }
}
