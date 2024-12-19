import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class CSVUtility {

    public static void removeDuplicates(String resultCsvFilePath) {
        File tempFile = new File(resultCsvFilePath + ".tmp");
        Set<String> seenLinks = new HashSet<>();

        try (
                Reader reader = new FileReader(resultCsvFilePath);
                CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                FileWriter writer = new FileWriter(tempFile);
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("original_link", "expected_link", "actual_link", "status"))
        ) {
            System.out.println("⏳ Eliminando duplicados en: " + resultCsvFilePath);

            for (CSVRecord record : csvParser) {
                String originalLink = record.get("original_link");
                if (seenLinks.add(originalLink)) {
                    csvPrinter.printRecord(
                            originalLink,
                            record.get("expected_link"),
                            record.get("actual_link"),
                            record.get("status")
                    );
                } else {
                    System.out.println("⏩ Registro duplicado eliminado: " + originalLink);
                }
            }
            csvPrinter.flush();
            System.out.println("✅ Duplicados eliminados en: " + resultCsvFilePath);
        } catch (IOException e) {
            System.out.println("⚠️ Error al eliminar duplicados: " + e.getMessage());
        }

        if (tempFile.exists()) {
            File originalFile = new File(resultCsvFilePath);
            if (originalFile.delete() && tempFile.renameTo(originalFile)) {
                System.out.println("✅ Archivo actualizado correctamente: " + resultCsvFilePath);
            } else {
                System.out.println("⚠️ Error al reemplazar archivo: " + resultCsvFilePath);
            }
        }
    }
}
