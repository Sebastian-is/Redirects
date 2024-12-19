import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.UnreachableBrowserException;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class RetryUtility {

    public static void retryFailedRecords(String resultCsvFilePath) {
        File tempFile = new File(resultCsvFilePath + ".tmp");

        try (
                Reader reader = new FileReader(resultCsvFilePath);
                CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                FileWriter writer = new FileWriter(tempFile);
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("original_link", "expected_link", "actual_link", "status"))
        ) {
            for (CSVRecord record : csvParser) {
                String originalLink = record.get("original_link");
                String expectedLink = record.get("expected_link");
                String actualLink = record.get("actual_link");
                String status = record.get("status");

                if ("Failed".equalsIgnoreCase(status)) {
                    System.out.println("🔄 Reintentando: " + originalLink);
                    WebDriver driver = null;

                    try {
                        driver = DriverFactory.initializeDriver("chrome");
                        for (int attempt = 1; attempt <= 3; attempt++) {
                            try {
                                System.out.println("Intento #" + attempt + " - URL: " + originalLink);
                                driver.get(originalLink);
                                actualLink = driver.getCurrentUrl();

                                if (expectedLink.equals(actualLink)) {
                                    status = "Success";
                                    System.out.println("✅ Redirección correcta: " + actualLink);
                                    break;
                                } else {
                                    System.out.println("⚠️ Redirección incorrecta, refrescando...");
                                    driver.navigate().refresh();
                                    TimeUnit.SECONDS.sleep(2);
                                }
                            } catch (UnreachableBrowserException e) {
                                System.out.println("⚠️ Navegador inalcanzable, reintentando...");
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("⚠️ Error procesando URL: " + originalLink + " - " + e.getMessage());
                    } finally {
                        if (driver != null) {
                            try {
                                DriverFactory.quitDriver(driver);
                            } catch (Exception e) {
                                System.out.println("⚠️ Error cerrando el navegador: " + e.getMessage());
                            }
                        }
                    }
                }

                // Escribir el registro, ya sea actualizado o no
                csvPrinter.printRecord(originalLink, expectedLink, actualLink, status);
            }

            csvPrinter.flush();
            System.out.println("✅ Archivo actualizado con los resultados reintentados: " + resultCsvFilePath);
        } catch (IOException e) {
            System.out.println("⚠️ Error al procesar registros fallidos: " + e.getMessage());
        }

        // Reemplazar el archivo original con el actualizado
        if (tempFile.exists()) {
            File originalFile = new File(resultCsvFilePath);
            if (originalFile.delete() && tempFile.renameTo(originalFile)) {
                System.out.println("✅ Archivo reemplazado correctamente: " + resultCsvFilePath);
            } else {
                System.out.println("⚠️ Error al reemplazar el archivo: " + resultCsvFilePath);
            }
        }
    }
}
