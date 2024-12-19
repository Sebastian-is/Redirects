    import org.apache.commons.csv.CSVFormat;
    import org.apache.commons.csv.CSVParser;
    import org.apache.commons.csv.CSVPrinter;
    import org.apache.commons.csv.CSVRecord;
    import org.junit.jupiter.api.Test;
    import org.openqa.selenium.WebDriver;
    import org.openqa.selenium.remote.UnreachableBrowserException;

    import java.io.*;
    import java.util.HashSet;
    import java.util.Set;
    import java.util.concurrent.ExecutorService;
    import java.util.concurrent.Executors;
    import java.util.concurrent.TimeUnit;

    public class RedirectTestFromCSV {

        @Test
        public void testRedirect() {
            String[] csvFilePaths = {
                    "src/test/resources/parte_1.csv",
                    "src/test/resources/parte_2.csv",
                    "src/test/resources/parte_3.csv"
            };

            String[] resultCsvFilePaths = {
                    "src/test/resources/redirect_results_part1.csv",
                    "src/test/resources/redirect_results_part2.csv",
                    "src/test/resources/redirect_results_part3.csv"
            };

            for (String resultCsv : resultCsvFilePaths) {
                CSVUtility.removeDuplicates(resultCsv);
            }

            int maxThreads = Math.min(csvFilePaths.length, Runtime.getRuntime().availableProcessors());
            ExecutorService executorService = Executors.newFixedThreadPool(maxThreads);

            try {
                for (int i = 0; i < csvFilePaths.length; i++) {
                    String inputCsv = csvFilePaths[i];
                    String outputCsv = resultCsvFilePaths[i];
                    fixHeaders(outputCsv);
                    executorService.submit(() -> processCsvFile(inputCsv, outputCsv));
                }

                executorService.shutdown();
                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                System.out.println("✅ Todos los archivos CSV han sido procesados.");
            } catch (Exception e) {
                System.out.println("⚠️ Error al iniciar los hilos: " + e.getMessage());
            }
        }

        @Test
        public void retryFailedTest() {
            String[] resultCsvFilePaths = {
                    "src/test/resources/redirect_results_part1.csv",
                    "src/test/resources/redirect_results_part2.csv",
                    "src/test/resources/redirect_results_part3.csv"
            };

            for (String resultCsv : resultCsvFilePaths) {
                RetryUtility.retryFailedRecords(resultCsv);
            }
        }

        private void processCsvFile(String csvFilePath, String resultCsvFilePath) {
            Set<String> processedLinks = new HashSet<>();

            try (
                    Reader reader = new FileReader(resultCsvFilePath);
                    CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())
            ) {
                for (CSVRecord record : csvParser) {
                    processedLinks.add(record.get("original_link"));
                }
                System.out.println("✅ Cargados " + processedLinks.size() + " registros ya procesados desde: " + resultCsvFilePath);
            } catch (IOException e) {
                System.out.println("⚠️ Error al cargar registros procesados: " + e.getMessage());
            }

            try (
                    Reader reader = new FileReader(csvFilePath);
                    CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader());
                    FileWriter writer = new FileWriter(resultCsvFilePath, true);
                    CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)
            ) {
                System.out.println("⏳ Procesando archivo: " + csvFilePath);

                for (CSVRecord record : csvParser) {
                    String originalLink = record.get("original_link");
                    if (processedLinks.contains(originalLink) || isDuplicate(originalLink, resultCsvFilePath)) {
                        System.out.println("⏩ Registro ya procesado, omitiendo: " + originalLink);
                        continue;
                    }

                    WebDriver driver = null;
                    String expectedLink = record.get("expected_link");
                    String actualLink = "";

                    String status = "Failed";

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
                                    Thread.sleep(2000);
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

                    csvPrinter.printRecord(originalLink, expectedLink, actualLink, status);
                    csvPrinter.flush();
                    System.out.println("Registro escrito: " + originalLink);
                    processedLinks.add(originalLink);
                }

                System.out.println("✅ Resultados guardados en: " + resultCsvFilePath);
            } catch (Exception e) {
                System.out.println("⚠️ Error procesando archivo: " + csvFilePath + " - " + e.getMessage());
            }
        }

        private void fixHeaders(String resultCsvFilePath) {
            File tempFile = new File(resultCsvFilePath + ".tmp");
            try (
                    Reader reader = new FileReader(resultCsvFilePath);
                    CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT);
                    FileWriter writer = new FileWriter(tempFile);
                    CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("original_link", "expected_link", "actual_link", "status"))
            ) {
                for (CSVRecord record : csvParser) {
                    csvPrinter.printRecord(record);
                }
                csvPrinter.flush();
                System.out.println("✅ Encabezados corregidos en: " + resultCsvFilePath);
            } catch (IOException e) {
                System.out.println("⚠️ Error al corregir encabezados: " + e.getMessage());
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

        private boolean isDuplicate(String originalLink, String resultCsvFilePath) {
            try (
                    Reader reader = new FileReader(resultCsvFilePath);
                    CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())
            ) {
                for (CSVRecord record : csvParser) {
                    if (record.get("original_link").equals(originalLink)) {
                        return true;
                    }
                }
            } catch (IOException e) {
                System.out.println("⚠️ Error verificando duplicados: " + e.getMessage());
            }
            return false;
        }
    }
