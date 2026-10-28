package com.example.backend;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    private final String EXCEL_PATH = "c:/Users/ACER/Desktop/stratup/link/All Newspapers.xlsx";
    private final String TEMP_DIR = "c:/Users/ACER/Desktop/stratup/temp";

    public File getExcelFile() {
        File f = new File(EXCEL_PATH);
        if (f.exists()) return f;
        File rel = new File("link/All Newspapers.xlsx");
        if (rel.exists()) return rel;
        File parent = new File("../link/All Newspapers.xlsx");
        if (parent.exists()) return parent;
        return f;
    }

    public List<String> getExcelSheets() throws Exception {
        List<String> sheets = new ArrayList<>();
        File excelFile = getExcelFile();
        if (!excelFile.exists()) return sheets;

        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheets.add(workbook.getSheetName(i));
            }
        }
        return sheets;
    }

    public List<String> getCitiesFromSheet(String sheetName) throws Exception {
        List<String> cities = new ArrayList<>();
        File excelFile = getExcelFile();
        if (!excelFile.exists()) return cities;

        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = (sheetName != null && !sheetName.isEmpty()) ? workbook.getSheet(sheetName) : workbook.getSheetAt(0);
            if (sheet == null) return cities;

            DataFormatter formatter = new DataFormatter();
            org.apache.poi.ss.usermodel.FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            java.util.Set<String> uniqueCities = new java.util.HashSet<>();
            
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header
                if (row.getCell(1) != null) {
                    String city = formatter.formatCellValue(row.getCell(1), evaluator);
                    if (city != null && !city.trim().isEmpty()) {
                        uniqueCities.add(city.trim());
                    }
                }
            }
            cities.addAll(uniqueCities);
            java.util.Collections.sort(cities);
        }
        return cities;
    }

    public List<String> getExtractedIdsByDate(String targetDate, String sheetName, String city) throws Exception {
        log.info("Starting extraction of IDs for date: {} on sheet: {} for city: {}", targetDate, sheetName, city);
        List<String> newsLinks = getLinksByDate(targetDate, sheetName, city);
        log.info("Found {} news links in Excel for date {}", newsLinks.size(), targetDate);
        
        List<String> articleIds = java.util.Collections.synchronizedList(new ArrayList<>());
        newsLinks.stream().forEach(link -> {
            log.info("Extracting article IDs from link: {}", link);
            List<String> extracted = extractArticleIds(link);
            articleIds.addAll(extracted);
            log.info("Extracted {} IDs from this link", extracted.size());
        });
        
        log.info("Total IDs extracted for date {}: {}", targetDate, articleIds.size());
        return articleIds;
    }

    public byte[] createPdfByDate(String targetDate, String sheetName, String city, boolean extractAds) throws Exception {
        if (extractAds) {
            log.info("Ad extraction requested for {}, {}. Currently a placeholder to call AI/OCR service.", targetDate, sheetName);
            // TODO: Call OCR/AI API to crop/extract ads from the pdf and return it.
        }
        if ("All".equalsIgnoreCase(city)) {
            List<String> cities = getCitiesFromSheet(sheetName);
            List<File> tempPdfs = new ArrayList<>();
            for (String c : cities) {
                if ("All".equalsIgnoreCase(c)) continue;
                byte[] pdfBytes = null;
                if ("Sakal".equalsIgnoreCase(sheetName)) {
                    pdfBytes = createSakalPdfByDate(targetDate, sheetName, c);
                } else if ("Loksatta today".equalsIgnoreCase(sheetName)) {
                    pdfBytes = createLoksattaPdfByDate(targetDate, sheetName, c);
                } else if ("Pudhari".equalsIgnoreCase(sheetName)) {
                    pdfBytes = createPudhariPdfByDate(targetDate, sheetName, c);
                } else if (sheetName.toLowerCase().contains("nagari")) {
                    pdfBytes = createPunyaNagariPdfByDate(targetDate, sheetName, c);
                } else if (sheetName.toLowerCase().contains("lokmat")) {
                    pdfBytes = createLokmatPdfByDate(targetDate, sheetName, c);
                } else {
                    // Default fallback logic, though not used by standard papers
                    continue; 
                }

                if (pdfBytes != null && pdfBytes.length > 0) {
                    File tempPdf = File.createTempFile("city_" + c.replaceAll("[^a-zA-Z0-9.-]", "_") + "_", ".pdf", new File(TEMP_DIR));
                    java.nio.file.Files.write(tempPdf.toPath(), pdfBytes);
                    tempPdfs.add(tempPdf);
                }
            }
            if (tempPdfs.isEmpty()) return new byte[0];
            byte[] merged = createPdfFromImagesList(tempPdfs);
            for (File f : tempPdfs) f.delete();
            return merged;
        }

        if ("Sakal".equalsIgnoreCase(sheetName)) {
            return createSakalPdfByDate(targetDate, sheetName, city);
        } else if ("Loksatta today".equalsIgnoreCase(sheetName)) {
            return createLoksattaPdfByDate(targetDate, sheetName, city);
        } else if ("Pudhari".equalsIgnoreCase(sheetName)) {
            return createPudhariPdfByDate(targetDate, sheetName, city);
        } else if ("Punya Nagari".equalsIgnoreCase(sheetName) || sheetName.toLowerCase().contains("nagari")) {
            return createPunyaNagariPdfByDate(targetDate, sheetName, city);
        } else if (sheetName.toLowerCase().contains("lokmat")) {
            return createLokmatPdfByDate(targetDate, sheetName, city);
        }

        log.info("Creating PDF for date: {} on sheet: {} for city: {}", targetDate, sheetName, city);
        List<String> articleIds = getExtractedIdsByDate(targetDate, sheetName, city);

        List<File> downloadedImages = java.util.Collections.synchronizedList(new ArrayList<>());
        
        articleIds.stream().forEach(id -> {
            log.info("Downloading images for article ID: {}", id);
            String articleUrl = "https://epaper.lokmat.com/articlepage.php?articleid=" + id;
            List<File> images = downloadImagesFromArticle(articleUrl, id);
            downloadedImages.addAll(images);
        });

        log.info("Successfully downloaded {} images. Generating PDF...", downloadedImages.size());
        
        // Sort images by the element id to ensure the PDF is in the correct order
        downloadedImages.sort((f1, f2) -> {
            try {
                String name1 = f1.getName().replace(".jpg", "");
                String name2 = f2.getName().replace(".jpg", "");
                String[] parts1 = name1.split("_");
                String[] parts2 = name2.split("_");
                
                int page1 = Integer.parseInt(parts1[parts1.length - 2]);
                int index1 = Integer.parseInt(parts1[parts1.length - 1]);
                
                int page2 = Integer.parseInt(parts2[parts2.length - 2]);
                int index2 = Integer.parseInt(parts2[parts2.length - 1]);
                
                if (page1 != page2) {
                    return Integer.compare(page1, page2);
                }
                return Integer.compare(index1, index2);
            } catch (Exception e) {
                return f1.getName().compareTo(f2.getName());
            }
        });

        byte[] pdf = createPdfFromImagesList(downloadedImages);
        log.info("PDF generated successfully for date {}", targetDate);
        return pdf;
    }

    private File downloadImageDirectly(String imageUrl, String fileName) {
        try {
            java.net.URL url = new java.net.URL(imageUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            String contentType = conn.getContentType();
            if (contentType != null && !contentType.toLowerCase().startsWith("image/")) {
                return null;
            }
            File tempDir = new File(TEMP_DIR);
            if (!tempDir.exists()) tempDir.mkdirs();
            File tempFile = new File(tempDir, fileName + ".jpg");
            try (InputStream in = conn.getInputStream();
                 java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return tempFile;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] createPudhariPdfByDate(String targetDate, String sheetName, String city) throws Exception {
        log.info("Creating Pudhari PDF for date: {} for city: {}", targetDate, city);
        
        String editionCode;
        if (city == null || city.isEmpty() || city.equalsIgnoreCase("Mumbai")) {
            editionCode = "PUDHARI_MUM";
        } else if (city.startsWith("PUDHARI_")) {
            editionCode = city;
        } else {
            editionCode = "PUDHARI_" + city;
        }
        
        String yyyyMMdd = targetDate.replace("-", "");
        String issueId = editionCode + "_" + yyyyMMdd;
        String metaUrl = "https://enewspapr.com/epaper-api/epaper-api.php?issueID=" + issueId + "&operation=getAllArticleByArticleId&cache=random";
        
        java.util.concurrent.ExecutorService pageExecutor = java.util.concurrent.Executors.newFixedThreadPool(4);
        java.util.concurrent.ExecutorService articleExecutor = java.util.concurrent.Executors.newFixedThreadPool(32);
        List<java.util.concurrent.CompletableFuture<File>> futures = new ArrayList<>();
        
        try {
            log.info("Fetching Pudhari metadata from {}", metaUrl);
            org.jsoup.Connection.Response res = Jsoup.connect(metaUrl).ignoreContentType(true).execute();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode pagesArray = mapper.readTree(res.body());
            
            int pageCount = 1;
            for (JsonNode pageNode : pagesArray) {
                if (pageNode.has("fullImagePath")) {
                    String imgUrl = pageNode.get("fullImagePath").asText();
                    if (imgUrl != null && !imgUrl.isEmpty()) {
                        log.info("Stitching Pudhari high-res page {} from {}", pageCount, imgUrl);
                        String fileName = "PUDHARI_" + targetDate + "_" + String.format("%02d", pageCount);
                        futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                            return downloadAndStitchPudhariImage(pageNode, imgUrl, fileName, articleExecutor);
                        }, pageExecutor));
                    }
                }
                pageCount++;
            }
        } catch (Exception e) {
            log.error("Error fetching Pudhari metadata from {}", metaUrl, e);
        }

        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        pageExecutor.shutdown();
        articleExecutor.shutdown();

        List<File> downloadedImages = new ArrayList<>();
        for (java.util.concurrent.CompletableFuture<File> f : futures) {
            File dest = f.join();
            if (dest != null) {
                downloadedImages.add(dest);
            }
        }

        if (downloadedImages.isEmpty()) return new byte[0];
        
        downloadedImages.sort((f1, f2) -> f1.getName().compareTo(f2.getName()));
        return createPdfFromImagesList(downloadedImages);
    }

    private File downloadAndStitchPudhariImage(JsonNode pageNode, String backgroundUrl, String fileName, java.util.concurrent.ExecutorService executor) {
        try {
            double SCALE = 3.0;
            java.awt.image.BufferedImage bgImg = null;
            java.net.URL url = new java.net.URL(backgroundUrl.replace("\\/", "/"));
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (java.io.InputStream in = conn.getInputStream()) {
                bgImg = javax.imageio.ImageIO.read(in);
            }
            if (bgImg == null) return null;
            
            int targetW = (int)(bgImg.getWidth() * SCALE);
            int targetH = (int)(bgImg.getHeight() * SCALE);
            
            java.awt.image.BufferedImage result = new java.awt.image.BufferedImage(targetW, targetH, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2 = result.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            
            g2.drawImage(bgImg, 0, 0, targetW, targetH, null);
            
            if (pageNode.has("Articles")) {
                JsonNode articles = pageNode.get("Articles");
                List<java.util.concurrent.CompletableFuture<Void>> artFutures = new ArrayList<>();
                java.util.Queue<Runnable> drawTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
                
                for (JsonNode articleWrap : articles) {
                    if (articleWrap.has("Article")) {
                        JsonNode article = articleWrap.get("Article");
                        if ((article.has("r2imagename") || article.has("imagename")) && article.has("x1") && article.has("y1") && article.has("w") && article.has("h")) {
                            artFutures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                                String artUrl = article.has("r2imagename") ? article.get("r2imagename").asText() : article.get("imagename").asText();
                                artUrl = artUrl.replace("\\/", "/");
                                int x1 = article.get("x1").asInt();
                                int y1 = article.get("y1").asInt();
                                int w = Integer.parseInt(article.get("w").asText().replace("px", "").trim());
                                int h = Integer.parseInt(article.get("h").asText().replace("px", "").trim());
                                
                                try {
                                    java.net.URL aUrl = new java.net.URL(artUrl);
                                    java.net.HttpURLConnection aConn = (java.net.HttpURLConnection) aUrl.openConnection();
                                    aConn.setRequestProperty("User-Agent", "Mozilla/5.0");
                                    try (java.io.InputStream in = aConn.getInputStream()) {
                                        java.awt.image.BufferedImage artImg = javax.imageio.ImageIO.read(in);
                                        if (artImg != null) {
                                            drawTasks.add(() -> g2.drawImage(artImg, (int)(x1 * SCALE), (int)(y1 * SCALE), (int)(w * SCALE), (int)(h * SCALE), null));
                                        }
                                    }
                                } catch (Exception ex) {
                                    log.warn("Failed to overlay Pudhari article {}", artUrl, ex);
                                }
                            }, executor));
                        }
                    }
                }
                java.util.concurrent.CompletableFuture.allOf(artFutures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
                for (Runnable r : drawTasks) {
                    r.run();
                }
            }
            g2.dispose();
            
            File tempDir = new File(TEMP_DIR);
            if (!tempDir.exists()) tempDir.mkdirs();
            File dest = new File(tempDir, fileName + ".jpg");
            javax.imageio.ImageIO.write(result, "jpg", dest);
            return dest;
            
        } catch (Exception e) {
            log.error("Failed to stitch Pudhari image, falling back...", e);
            return downloadImageDirectly(backgroundUrl, fileName);
        }
    }

    private File downloadAndStitchLoksattaImage(JsonNode levels, String fileName, java.util.concurrent.ExecutorService executor) {
        try {
            JsonNode bestLevel = null;
            if (levels.has("level3")) bestLevel = levels.get("level3");
            else if (levels.has("level2")) bestLevel = levels.get("level2");
            else if (levels.has("level1")) bestLevel = levels.get("level1");
            else bestLevel = levels.get("leveldefault");

            if (bestLevel == null || !bestLevel.has("chunks")) return null;
            
            JsonNode chunks = bestLevel.get("chunks");
            List<JsonNode> jpgChunks = new ArrayList<>();
            for (JsonNode chunk : chunks) {
                if (chunk.get("url").asText().toLowerCase().endsWith(".jpg")) {
                    jpgChunks.add(chunk);
                }
            }
            if (jpgChunks.isEmpty()) return null;

            int totalW = 0;
            int totalH = 0;
            for (JsonNode chunk : jpgChunks) {
                int right = chunk.get("tx").asInt() + chunk.get("width").asInt();
                int bottom = chunk.get("ty").asInt() + chunk.get("height").asInt();
                if (right > totalW) totalW = right;
                if (bottom > totalH) totalH = bottom;
            }

            java.awt.image.BufferedImage result = new java.awt.image.BufferedImage(totalW, totalH, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2 = result.createGraphics();
            java.util.List<java.util.concurrent.CompletableFuture<Void>> chunkFutures = new ArrayList<>();
            java.util.Queue<Runnable> drawTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();

            for (JsonNode chunk : jpgChunks) {
                chunkFutures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        String urlStr = chunk.get("url").asText().replace("\\/", "/");
                        java.net.URL url = new java.net.URL(urlStr);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        try (java.io.InputStream in = conn.getInputStream()) {
                            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
                            if (img != null) {
                                drawTasks.add(() -> g2.drawImage(img, chunk.get("tx").asInt(), chunk.get("ty").asInt(), null));
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to download Loksatta chunk", ex);
                    }
                }, executor));
            }
            java.util.concurrent.CompletableFuture.allOf(chunkFutures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            for (Runnable task : drawTasks) {
                task.run();
            }
            g2.dispose();

            File tempDir = new File(TEMP_DIR);
            if (!tempDir.exists()) tempDir.mkdirs();
            File dest = new File(tempDir, fileName + ".jpg");
            javax.imageio.ImageIO.write(result, "jpg", dest);
            return dest;

        } catch (Exception e) {
            log.error("Failed to stitch Loksatta image, falling back...", e);
            // Fallback to the first chunk of leveldefault
            try {
                String fallbackUrl = levels.get("leveldefault").get("chunks").get(0).get("url").asText().replace("\\/", "/");
                return downloadImageDirectly(fallbackUrl, fileName);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private File downloadAndStitchSakalImage(String flUrl, String fileName, java.util.concurrent.ExecutorService executor) {
        try {
            String base = flUrl.replace("_FL.jpg", "/S/");
            byte[][] chunks = new byte[9][];
            java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < 9; i++) {
                final int idx = i;
                futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        java.net.URL url = new java.net.URL(base + idx + ".jpg");
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        try (java.io.InputStream in = conn.getInputStream()) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int n;
                            while((n = in.read(buf)) != -1) baos.write(buf, 0, n);
                            chunks[idx] = baos.toByteArray();
                        }
                    } catch (Exception e) {
                        log.warn("Sakal chunk download failed for idx " + idx);
                    }
                }, executor));
            }
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            
            if (chunks[0] == null) return null;
            
            File tempDir = new File(TEMP_DIR);
            if (!tempDir.exists()) tempDir.mkdirs();
            File dest = new File(tempDir, fileName + ".pdf");
            
            try (org.apache.pdfbox.pdmodel.PDDocument document = new org.apache.pdfbox.pdmodel.PDDocument()) {
                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject firstChunk = org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(document, chunks[0], "chunk0");
                int chunkW = firstChunk.getWidth();
                int chunkH = firstChunk.getHeight();
                int totalW = chunkW * 3;
                int totalH = chunkH * 3;
                
                org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(totalW, totalH));
                document.addPage(page);
                
                try (org.apache.pdfbox.pdmodel.PDPageContentStream contentStream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                    for (int i = 0; i < 9; i++) {
                        if (chunks[i] != null) {
                            int row = i / 3;
                            int col = i % 3;
                            org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject pdChunk = (i == 0) ? firstChunk : org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(document, chunks[i], "chunk" + i);
                            float pdfY = totalH - (row * chunkH) - chunkH;
                            contentStream.drawImage(pdChunk, col * chunkW, pdfY, chunkW, chunkH);
                        }
                    }
                }
                document.save(dest);
            }
            return dest;
        } catch (Exception e) {
            log.error("Failed to stitch Sakal image from {}, falling back to direct download...", flUrl, e);
            return downloadImageDirectly(flUrl, fileName);
        }
    }

    private byte[] createLoksattaPdfByDate(String targetDate, String sheetName, String city) throws Exception {
        log.info("Creating Loksatta PDF for date: {} for city: {}", targetDate, city);
        String excelUrl = null;
        File excelFile = getExcelFile();
        if (excelFile.exists()) {
            try (FileInputStream fis = new FileInputStream(excelFile);
                 Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(fis)) {
                Sheet sheet = workbook.getSheet("Loksatta today");
                if (sheet != null) {
                    org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
                    org.apache.poi.ss.usermodel.FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                    for (Row row : sheet) {
                        if (row.getRowNum() == 0) continue;
                        if (row.getCell(7) != null) {
                            String link = formatter.formatCellValue(row.getCell(7), evaluator);
                            if (link != null && (city == null || city.isEmpty() || link.toLowerCase().contains(city.toLowerCase()))) {
                                excelUrl = link;
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (excelUrl == null || excelUrl.trim().isEmpty()) {
            log.warn("Could not find excel URL for Loksatta");
            return new byte[0];
        }

        String titleId = null;
        try {
            org.jsoup.Connection.Response htmlRes = Jsoup.connect(excelUrl).ignoreContentType(true).execute();
            String html = htmlRes.body();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("'titleId':\\s*(\\d+)").matcher(html);
            if (m.find()) {
                titleId = m.group(1);
            }
        } catch (Exception e) {
            log.error("Failed to fetch HTML for titleId", e);
        }
        
        if (titleId == null) {
            log.warn("Could not extract titleId for Loksatta from " + excelUrl);
            return new byte[0];
        }

        long epoch = java.time.LocalDate.parse(targetDate).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
        long startEpoch = epoch - (86400 * 15);
        long endEpoch = epoch + (86400 * 15);
        
        String issueId = null;
        try {
            String datesUrl = "https://epaper.loksatta.com/viewer/publishdates/" + titleId + "/" + startEpoch + "/" + endEpoch + "/json";
            org.jsoup.Connection.Response datesRes = Jsoup.connect(datesUrl).ignoreContentType(true).execute();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode datesArray = mapper.readTree(datesRes.body());
            for (JsonNode node : datesArray) {
                if (node.has("published") && node.get("published").asText().startsWith(targetDate)) {
                    issueId = node.get("id").asText();
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch publishdates", e);
        }

        if (issueId == null) {
            log.warn("Could not find issueId for date " + targetDate);
            return new byte[0];
        }

        List<File> downloadedImages = new ArrayList<>();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
        List<java.util.concurrent.CompletableFuture<File>> futures = new ArrayList<>();
        
        try {
            String metaUrl = "https://epaper.loksatta.com/pagemeta/get/" + issueId + "/1-50";
            log.info("Fetching Loksatta pagemeta from {}", metaUrl);
            org.jsoup.Connection.Response res = Jsoup.connect(metaUrl).ignoreContentType(true).execute();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(res.body());
            
            for (int i = 1; i <= 50; i++) {
                JsonNode pageNode = root.get(String.valueOf(i));
                if (pageNode == null) break;
                
                JsonNode levels = pageNode.get("levels");
                if (levels == null) break;
                
                String fileName = "LOKSATTA_" + targetDate + "_" + String.format("%02d", i);
                futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    return downloadAndStitchLoksattaImage(levels, fileName, executor);
                }, executor));
            }
        } catch (Exception e) {
            log.error("Failed to parse Loksatta JSON", e);
        }
        
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        executor.shutdown();
        
        for (java.util.concurrent.CompletableFuture<File> f : futures) {
            File dest = f.join();
            if (dest != null) {
                downloadedImages.add(dest);
            }
        }
        
        if (downloadedImages.isEmpty()) return new byte[0];
        return createPdfFromImagesList(downloadedImages);
    }

    private byte[] createPunyaNagariPdfByDate(String targetDate, String sheetName, String city) throws Exception {
        log.info("Creating Punya Nagari PDF for date: {} for city: {}", targetDate, city);
        
        String[] dateParts = targetDate.split("-");
        if (dateParts.length != 3) return new byte[0];
        
        String ddMMyyyy = dateParts[2] + "/" + dateParts[1] + "/" + dateParts[0];
        String ced = city != null && !city.isEmpty() ? city : "11";
        
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newCachedThreadPool();
        List<java.util.concurrent.CompletableFuture<java.util.AbstractMap.SimpleEntry<Integer, File>>> futures = new ArrayList<>();
        
        for (int i = 1; i <= 30; i++) {
            final int page = i;
            futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                String pageUrl = "https://epaper.punyanagari.in/?url=home&ced=" + ced + "&date=" + ddMMyyyy + "&page=" + page;
                try {
                    org.jsoup.Connection.Response res = Jsoup.connect(pageUrl).userAgent("Mozilla/5.0").execute();
                    String html = res.body();
                    
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("compressed_[a-zA-Z0-9_]+\\.jpg").matcher(html);
                    if (!m.find()) return null;
                    
                    String imgName = m.group(0);
                    
                    String mmYYYY = dateParts[1] + dateParts[0];
                    String flUrl = "https://epaper.punyanagari.in/assets/public/epaper/edition/" + mmYYYY + "/" + imgName;
                    
                    File dest = downloadImageDirectly(flUrl, "PUNYANAGARI_" + targetDate + "_" + String.format("%02d", page));
                    if (dest != null) {
                        return new java.util.AbstractMap.SimpleEntry<>(page, dest);
                    }
                } catch (Exception e) {
                    log.warn("Punya Nagari page {} download failed", page);
                }
                return null;
            }, executor));
        }
        
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
        executor.shutdown();
        
        List<java.util.AbstractMap.SimpleEntry<Integer, File>> validPages = new ArrayList<>();
        for (java.util.concurrent.CompletableFuture<java.util.AbstractMap.SimpleEntry<Integer, File>> f : futures) {
            java.util.AbstractMap.SimpleEntry<Integer, File> result = f.join();
            if (result != null) {
                validPages.add(result);
            }
        }
        
        // Punya Nagari loops back to page 1. We must find the first duplicate file length or looping page.
        // Or simply group them and ensure we don't have duplicates, but actually we can just take valid ones
        // up to the point they start repeating sizes.
        validPages.sort((p1, p2) -> p1.getKey().compareTo(p2.getKey()));
        
        List<File> downloadedImages = new ArrayList<>();
        java.util.Set<Long> seenSizes = new java.util.HashSet<>();
        
        for (java.util.AbstractMap.SimpleEntry<Integer, File> entry : validPages) {
            long size = entry.getValue().length();
            if (seenSizes.contains(size)) {
                entry.getValue().delete(); // delete duplicate
                continue; // assume this is the loop back to page 1
            }
            seenSizes.add(size);
            downloadedImages.add(entry.getValue());
        }

        if (downloadedImages.isEmpty()) return new byte[0];
        return createPdfFromImagesList(downloadedImages);
    }


    private byte[] createLokmatPdfByDate(String targetDate, String sheetName, String city) throws Exception {
        log.info("Creating Lokmat PDF for date: {} for city: {}", targetDate, city);
        List<String> links = getLinksByDate(targetDate, sheetName, city);
        if (links.isEmpty()) {
            return new byte[0];
        }

        java.util.Set<String> processedIssues = java.util.concurrent.ConcurrentHashMap.newKeySet();
        List<File> downloadedImages = java.util.Collections.synchronizedList(new ArrayList<>());
        
        links.stream().forEach(link -> {
            try {
                Document doc = Jsoup.connect(link).userAgent("Mozilla/5.0").get();
                String html = doc.html();
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("var\\s+issueidPdf\\s*=\\s*\"([^\"]+)\"");
                java.util.regex.Matcher m = p.matcher(html);
                if (m.find()) {
                    String issueId = m.group(1);
                    if (!processedIssues.add(issueId)) {
                        return; // Already processed this issue
                    }
                    String[] parts = issueId.split("_");
                    if (parts.length >= 3) {
                        String issuename = parts[0];
                        String editionname = parts[1];
                        String dateStr = parts[2]; // e.g. 20261009
                        String year = dateStr.substring(0, 4);
                        String month = dateStr.substring(4, 6);
                        String date = dateStr.substring(6, 8);
                        
                        String apiUrl = "https://epaperlokmat.in/eNewspaper/OutSourcingDataNew.php?operation=getPageArticleDetails&selectedIssueId=" + issueId + "&data=3";
                        Document apiDoc = Jsoup.connect(apiUrl).ignoreContentType(true).userAgent("Mozilla/5.0").get();
                        String response = apiDoc.text();
                        
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode pages = mapper.readTree(response);
                        
                        java.util.concurrent.ExecutorService pageExecutor = java.util.concurrent.Executors.newFixedThreadPool(4);
                        java.util.concurrent.ExecutorService articleExecutor = java.util.concurrent.Executors.newFixedThreadPool(32);
                        java.util.List<java.util.concurrent.CompletableFuture<Void>> pageFutures = new ArrayList<>();
                        
                        pages.forEach(pageNode -> {
                            pageFutures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                                try {
                                    String pageNum = pageNode.get("pageno").asText();
                                    String bgImageName = pageNode.get("imagename").asText();
                                    
                                    String bgImageUrl = "https://images.epaperlokmat.in/eNewspaper/News/" + issuename + "/" + editionname + "/" + year + "/" + month + "/" + date + "/" + bgImageName;
                                    log.info("Downloading Lokmat full page {} image: {}", pageNum, bgImageUrl);
                                    
                                    File dest = downloadImageDirectly(bgImageUrl, issueId + "_" + pageNum + "_bg.jpg");
                                    if (dest != null) {
                                        try {
                                            java.awt.image.BufferedImage bgImg = javax.imageio.ImageIO.read(dest);
                                            double SCALE = 2.5;
                                            int masterWidth = (int)(bgImg.getWidth() * SCALE);
                                            int masterHeight = (int)(bgImg.getHeight() * SCALE);
                                            
                                            java.awt.image.BufferedImage result = new java.awt.image.BufferedImage(masterWidth, masterHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);
                                            java.awt.Graphics2D g2 = result.createGraphics();
                                            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                            g2.drawImage(bgImg, 0, 0, masterWidth, masterHeight, null);
                                            
                                            JsonNode articles = pageNode.get("Articles");
                                            if (articles != null && articles.isArray()) {
                                                java.util.List<java.util.concurrent.CompletableFuture<Void>> articleFutures = new ArrayList<>();
                                                java.util.Queue<Runnable> drawTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
                                                
                                                articles.forEach(articleWrapper -> {
                                                    articleFutures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                                                        try {
                                                            JsonNode article = articleWrapper.get("Article");
                                                            if (article != null) {
                                                                String artImageName = article.get("imagename").asText();
                                                                String artUrl = "https://images.epaperlokmat.in/eNewspaper/" + artImageName;
                                                                int x1 = article.get("x1").asInt();
                                                                int y1 = article.get("y1").asInt();
                                                                int w = article.get("x2").asInt() - x1;
                                                                int h = article.get("y2").asInt() - y1;
                                                                
                                                                try {
                                                                    java.net.URL aUrl = new java.net.URL(artUrl);
                                                                    java.net.HttpURLConnection aConn = (java.net.HttpURLConnection) aUrl.openConnection();
                                                                    aConn.setRequestProperty("User-Agent", "Mozilla/5.0");
                                                                    aConn.setConnectTimeout(15000);
                                                                    aConn.setReadTimeout(15000);
                                                                    try (java.io.InputStream in = aConn.getInputStream()) {
                                                                        java.awt.image.BufferedImage artImg = javax.imageio.ImageIO.read(in);
                                                                        if (artImg != null) {
                                                                            drawTasks.add(() -> {
                                                                                g2.drawImage(artImg, (int)(x1 * SCALE), (int)(y1 * SCALE), (int)(w * SCALE), (int)(h * SCALE), null);
                                                                            });
                                                                        }
                                                                    }
                                                                } catch (Exception ex) {
                                                                    log.warn("Failed Lokmat article", ex);
                                                                }
                                                            }
                                                        } catch (Exception e) {
                                                            log.warn("Failed to fetch Lokmat article", e);
                                                        }
                                                    }, articleExecutor));
                                                });
                                                java.util.concurrent.CompletableFuture.allOf(articleFutures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
                                                
                                                for (Runnable task : drawTasks) {
                                                    task.run();
                                                }
                                            }
                                            g2.dispose();
                                            
                                            File highResDest = new File(TEMP_DIR, issueId + "_" + pageNum + ".jpg");
                                            javax.imageio.ImageIO.write(result, "jpg", highResDest);
                                            dest.delete(); // delete low res bg
                                            
                                            downloadedImages.add(highResDest);
                                        } catch (Exception e) {
                                            log.warn("Failed to stitch Lokmat page, using low res", e);
                                            downloadedImages.add(dest);
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("Error processing Lokmat page node", e);
                                }
                            }, pageExecutor));
                        });
                        
                        java.util.concurrent.CompletableFuture.allOf(pageFutures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
                        pageExecutor.shutdown();
                        articleExecutor.shutdown();
                    }
                }
            } catch (Exception e) {
                log.error("Error processing Lokmat link {}", link, e);
            }
        });

        if (downloadedImages.isEmpty()) return new byte[0];
        
        // Sort by page number
        downloadedImages.sort((f1, f2) -> {
            try {
                String n1 = f1.getName().replace(".jpg", "");
                String n2 = f2.getName().replace(".jpg", "");
                String[] p1 = n1.split("_");
                String[] p2 = n2.split("_");
                int pg1 = Integer.parseInt(p1[p1.length - 1]);
                int pg2 = Integer.parseInt(p2[p2.length - 1]);
                return Integer.compare(pg1, pg2);
            } catch (Exception e) {
                return f1.getName().compareTo(f2.getName());
            }
        });
        
        return createPdfFromImagesList(downloadedImages);
    }

    private byte[] createSakalPdfByDate(String targetDate, String sheetName, String city) throws Exception {
        log.info("Creating Sakal PDF for date: {} for city: {}", targetDate, city);
        
        List<String> links = getLinksByDate(targetDate, sheetName, city);
        if (links.isEmpty()) {
            return new byte[0];
        }
        
        List<File> downloadedImages = java.util.Collections.synchronizedList(new ArrayList<>());
        
        links.stream().forEach(pageUrl -> {
            try {
                log.info("Fetching Sakal page data from: {}", pageUrl);
                Document doc = Jsoup.connect(pageUrl).userAgent("Mozilla/5.0").get();
                String html = doc.html();
                
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("var\\s+allPagesData\\s*=\\s*(\\[.*?\\]);");
                java.util.regex.Matcher m = p.matcher(html);
                
                if (m.find()) {
                    String json = m.group(1);
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readTree(json);
                    
                    List<JsonNode> pages = new ArrayList<>();
                    rootNode.forEach(pages::add);
                    
                    // We must fetch pages sequentially or in a nested parallel stream? 
                    // To prevent ForkJoinPool exhaustion, we use sequential stream for pages here.
                    pages.stream().forEach(pageNode -> {
                        try {
                            String thUrl = pageNode.has("thumnailurl") ? pageNode.get("thumnailurl").asText() : "";
                            if (!thUrl.isEmpty()) {
                                String flUrl = thUrl.replace("_Th.jpg", "_FL.jpg").replace("Data//", "Data/");
                                int pageNo = pageNode.get("pageno").asInt();
                                
                                log.info("Stitching Sakal high-res chunks for page {} from {}", pageNo, flUrl);
                                String fileName = "SAKAL_" + targetDate + "_" + String.format("%02d", pageNo);
                                File dest = downloadImageDirectly(flUrl, fileName);
                                if (dest != null) {
                                    downloadedImages.add(dest);
                                } else {
                                    log.warn("Failed to stitch Sakal page {}", pageNo);
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error downloading Sakal image", e);
                        }
                    });
                }
            } catch (Exception ex) {
                log.error("Failed to process Sakal link: {}", pageUrl, ex);
            }
        });
        
        downloadedImages.sort((f1, f2) -> f1.getName().compareTo(f2.getName()));
        
        return createPdfFromImagesList(downloadedImages);
    }

    public byte[] createAllNewspapersPdfByDate(String targetDate, String city, boolean extractAds) throws Exception {
        List<String> sheets = getExcelSheets();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        org.apache.pdfbox.multipdf.PDFMergerUtility pdfMerger = new org.apache.pdfbox.multipdf.PDFMergerUtility();
        pdfMerger.setDestinationStream(baos);
        
        List<java.io.ByteArrayInputStream> streams = new ArrayList<>();
        try {
            List<java.util.concurrent.CompletableFuture<byte[]>> futures = new ArrayList<>();
            int[] workerPorts = {8081, 8082, 8083};
            int workerIdx = 0;
            
            for (String sheet : sheets) {
                final int targetPort = workerPorts[workerIdx % workerPorts.length];
                workerIdx++;
                
                futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        log.info("Delegating {} to worker instance at port {}", sheet, targetPort);
                        String encodedSheet = java.net.URLEncoder.encode(sheet, "UTF-8");
                        String encodedCity = city != null ? java.net.URLEncoder.encode(city, "UTF-8") : "";
                        String urlStr = "http://localhost:" + targetPort + "/api/pdf/by-date?date=" + targetDate + "&sheetName=" + encodedSheet + "&extractAds=" + extractAds;
                        if (!encodedCity.isEmpty()) {
                            urlStr += "&city=" + encodedCity;
                        }
                        
                        java.net.URL url = new java.net.URL(urlStr);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(60000);
                        conn.setReadTimeout(600000); // 10 minutes max wait
                        
                        if (conn.getResponseCode() == 200) {
                            try (java.io.InputStream in = conn.getInputStream()) {
                                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                                byte[] data = new byte[8192];
                                int nRead;
                                while ((nRead = in.read(data, 0, data.length)) != -1) {
                                    buffer.write(data, 0, nRead);
                                }
                                return buffer.toByteArray();
                            }
                        } else {
                            log.warn("Worker at port {} returned {} for {}, falling back to direct generation", targetPort, conn.getResponseCode(), sheet);
                            return createPdfByDate(targetDate, sheet, city, extractAds);
                        }
                    } catch (Exception e) {
                        log.info("Worker instance at port {} unavailable for {}. Executing direct in-process generation...", targetPort, sheet);
                        try {
                            return createPdfByDate(targetDate, sheet, city, extractAds);
                        } catch (Exception directEx) {
                            log.error("Direct generation also failed for sheet {}", sheet, directEx);
                            return null;
                        }
                    }
                }));
            }
            
            for (java.util.concurrent.CompletableFuture<byte[]> future : futures) {
                try {
                    byte[] pdf = future.join();
                    if (pdf != null && pdf.length > 0) {
                        java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(pdf);
                        streams.add(bais);
                        pdfMerger.addSource(new org.apache.pdfbox.io.RandomAccessReadBuffer(pdf));
                    }
                } catch (Exception e) {
                    log.error("Error joining parallel PDF futures", e);
                }
            }
            if (!streams.isEmpty()) {
                pdfMerger.mergeDocuments(org.apache.pdfbox.io.IOUtils.createMemoryOnlyStreamCache());
            }
        } finally {
            for (java.io.ByteArrayInputStream stream : streams) {
                try { stream.close(); } catch (Exception ignored) {}
            }
        }
        return baos.toByteArray();
    }

    public byte[] createPdfsByDateRange(String startDate, String endDate, String sheetName, String city, boolean extractAds) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            List<String> sheets = "All".equalsIgnoreCase(sheetName) ? getExcelSheets() : java.util.Collections.singletonList(sheetName);
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                LocalDate start = LocalDate.parse(startDate, formatter);
                LocalDate end = LocalDate.parse(endDate, formatter);
                
                for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                    String dateStr = date.format(formatter);
                    for (String sheet : sheets) {
                        try {
                            byte[] pdf = createPdfByDate(dateStr, sheet, city, extractAds);
                            if (pdf != null && pdf.length > 0) {
                                String prefix = "All".equalsIgnoreCase(sheetName) ? sheet.replaceAll("[^a-zA-Z0-9.-]", "_") + "_" : "";
                                ZipEntry entry = new ZipEntry(prefix + "News_" + dateStr + ".pdf");
                                zos.putNextEntry(entry);
                                zos.write(pdf);
                                zos.closeEntry();
                            }
                        } catch (Exception e) {
                            log.error("Error creating pdf for {} on {}", sheet, dateStr, e);
                        }
                    }
                }
            } catch (DateTimeParseException e) {
                // Fallback to simple array if they are not in yyyyMMdd format
                String[] dates = {startDate, endDate};
                for (String date : dates) {
                    if (date == null || date.isEmpty()) continue;
                    for (String sheet : sheets) {
                        try {
                            byte[] pdf = createPdfByDate(date, sheet, city, extractAds);
                            if (pdf != null && pdf.length > 0) {
                                String prefix = "All".equalsIgnoreCase(sheetName) ? sheet.replaceAll("[^a-zA-Z0-9.-]", "_") + "_" : "";
                                ZipEntry entry = new ZipEntry(prefix + "News_" + date + ".pdf");
                                zos.putNextEntry(entry);
                                zos.write(pdf);
                                zos.closeEntry();
                            }
                        } catch (Exception ex) {
                            log.error("Error creating pdf for {} on {}", sheet, date, ex);
                        }
                    }
                }
            }
        }
        return baos.toByteArray();
    }

    public byte[] createPdfFromImagesList(List<File> files) throws Exception {
        if (files.isEmpty()) {
            return new byte[0];
        }

        boolean hasPdfs = files.stream().anyMatch(f -> f.getName().toLowerCase().endsWith(".pdf"));
        if (hasPdfs) {
            org.apache.pdfbox.multipdf.PDFMergerUtility pdfMerger = new org.apache.pdfbox.multipdf.PDFMergerUtility();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pdfMerger.setDestinationStream(baos);
            List<org.apache.pdfbox.io.RandomAccessRead> streamsToClose = new ArrayList<>();
            for (File f : files) {
                if (f.getName().toLowerCase().endsWith(".pdf")) {
                    pdfMerger.addSource(f);
                } else {
                    try (PDDocument tempDoc = new PDDocument()) {
                        PDImageXObject pdImage = PDImageXObject.createFromFile(f.getAbsolutePath(), tempDoc);
                        float imgWidth = pdImage.getWidth();
                        float imgHeight = pdImage.getHeight();
                        float A4_WIDTH = org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getWidth();
                        float scale = (imgWidth > A4_WIDTH) ? (A4_WIDTH / imgWidth) : 1.0f;
                        float pageWidth = imgWidth * scale;
                        float pageHeight = imgHeight * scale;
                        PDPage page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(pageWidth, pageHeight));
                        tempDoc.addPage(page);
                        try (PDPageContentStream contentStream = new PDPageContentStream(tempDoc, page)) {
                            contentStream.drawImage(pdImage, 0, 0, pageWidth, pageHeight);
                        }
                        ByteArrayOutputStream tempBaos = new ByteArrayOutputStream();
                        tempDoc.save(tempBaos);
                        org.apache.pdfbox.io.RandomAccessReadBuffer buffer = new org.apache.pdfbox.io.RandomAccessReadBuffer(tempBaos.toByteArray());
                        pdfMerger.addSource(buffer);
                        streamsToClose.add(buffer);
                    }
                }
            }
            pdfMerger.mergeDocuments(null);
            for (org.apache.pdfbox.io.RandomAccessRead is : streamsToClose) {
                is.close();
            }
            for (File f : files) {
                f.delete();
            }
            return baos.toByteArray();
        }

        try (PDDocument document = new PDDocument()) {
            for (File imageFile : files) {
                PDImageXObject pdImage = PDImageXObject.createFromFile(imageFile.getAbsolutePath(), document);
                
                float imgWidth = pdImage.getWidth();
                float imgHeight = pdImage.getHeight();
                
                // Keep dimensions reasonable to avoid PDF viewer blurring
                float A4_WIDTH = org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getWidth();
                float scale = (imgWidth > A4_WIDTH) ? (A4_WIDTH / imgWidth) : 1.0f;
                float pageWidth = imgWidth * scale;
                float pageHeight = imgHeight * scale;
                
                PDPage page = new PDPage(new org.apache.pdfbox.pdmodel.common.PDRectangle(pageWidth, pageHeight));
                document.addPage(page);
                
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.drawImage(pdImage, 0, 0, pageWidth, pageHeight);
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        } finally {
            // Clean up temporary images
            for (File img : files) {
                img.delete();
            }
        }
    }

    private List<String> getLinksByDate(String targetDate, String sheetName, String city) throws Exception {
        List<String> links = new ArrayList<>();
        File excelFile = getExcelFile();
        if (!excelFile.exists()) return links;

        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = (sheetName != null && !sheetName.isEmpty()) ? workbook.getSheet(sheetName) : workbook.getSheetAt(0);
            if (sheet == null) return links;

            DataFormatter formatter = new DataFormatter();
            org.apache.poi.ss.usermodel.FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header
                
                if (row.getCell(5) != null) {
                    String link = formatter.formatCellValue(row.getCell(5), evaluator);
                    if (link != null && !link.trim().isEmpty() && link.startsWith("http")) {
                        // Check if city matches (if city is provided)
                        if (city != null && !city.trim().isEmpty() && !"All".equalsIgnoreCase(city.trim())) {
                            // Match the city against the URL since Col B and Col F are not aligned
                            String decodedLink = java.net.URLDecoder.decode(link, java.nio.charset.StandardCharsets.UTF_8.name());
                            if (!decodedLink.toLowerCase().contains(city.toLowerCase())) {
                                continue;
                            }
                        }

                        // The links in Excel contain a hardcoded date, e.g., 2026-10-03
                        // We must replace this date with the targetDate requested by the user.
                        // Format the targetDate to match YYYY-MM-DD
                        try {
                            java.time.LocalDate tDate = java.time.LocalDate.parse(targetDate);
                            String formattedDate = tDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                            
                            // Replace the date in the URL using a regex pattern
                            link = link.replaceAll("\\d{4}-\\d{2}-\\d{2}", formattedDate);
                            links.add(link.trim());
                        } catch (Exception e) {
                            log.error("Failed to parse date: {}", targetDate);
                        }
                    }
                }
            }
        }
        return links;
    }

    private List<String> extractArticleIds(String url) {
        List<String> ids = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
            String html = doc.html();
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("var\\s+issueidPdf\\s*=\\s*\"([^\"]+)\"");
            java.util.regex.Matcher m = p.matcher(html);
            if (m.find()) {
                String issueId = m.group(1);
                String[] parts = url.split("/");
                String pageStr = parts[parts.length - 1];
                int pageNum = Integer.parseInt(pageStr);
                
                for (int i = 1; i <= 20; i++) {
                    try {
                        String articleId = issueId + "_" + pageNum + "_" + i;
                        String articleUrl = "https://epaper.lokmat.com/articlepage.php?articleid=" + articleId;
                        Document articleDoc = Jsoup.connect(articleUrl).userAgent("Mozilla/5.0").get();
                        org.jsoup.nodes.Element meta = articleDoc.selectFirst("meta[property=og:image]");
                        if (meta != null) {
                            String imgUrl = meta.attr("content");
                            if (imgUrl != null && imgUrl.contains("lokmat-logo-metatgs.jpg")) {
                                break;
                            }
                            ids.add(articleId);
                        } else {
                            break;
                        }
                    } catch (Exception loopException) {
                        // ignore errors and stop checking this page just in case
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ids;
    }

    private List<File> downloadImagesFromArticle(String url, String articleId) {
        List<File> imageFiles = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
            Element meta = doc.selectFirst("meta[property=og:image]");
            
            if (meta != null) {
                String src = meta.attr("content");
                if (src != null && !src.isEmpty() && !src.contains("lokmat-logo-metatgs.jpg")) {
                    File tempDir = new File(TEMP_DIR);
                    if (!tempDir.exists()) tempDir.mkdirs();
                    
                    String fileName = articleId + ".jpg";
                    File dest = new File(tempDir, fileName);
                    
                    try {
                        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new URL(src).openConnection();
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                        try (InputStream in = connection.getInputStream()) {
                            Files.copy(in, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            imageFiles.add(dest);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return imageFiles;
    }
}
