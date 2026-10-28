package com.example.backend;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.ArrayList;

public class CheckSmartExtraction {
    public static void main(String[] args) throws Exception {
        String url = "http://epaper.lokmat.com/main-editions/Mumbai%20Main/2026-10-03/01";
        Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
        String html = doc.html();
        
        Pattern p = Pattern.compile("var\\s+issueidPdf\\s*=\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(html);
        if (m.find()) {
            String issueId = m.group(1); // e.g. LOK_MULK_20261003
            System.out.println("Found issueId: " + issueId);
            
            // Extract page number from URL (last part)
            String[] parts = url.split("/");
            String pageStr = parts[parts.length - 1];
            int pageNum = Integer.parseInt(pageStr); // 01 -> 1
            
            System.out.println("Page Num: " + pageNum);
            
            List<String> imageUrls = new ArrayList<>();
            for (int i = 1; i <= 20; i++) {
                String articleId = issueId + "_" + pageNum + "_" + i;
                String articleUrl = "https://epaper.lokmat.com/articlepage.php?articleid=" + articleId;
                Document articleDoc = Jsoup.connect(articleUrl).userAgent("Mozilla/5.0").get();
                Element meta = articleDoc.selectFirst("meta[property=og:image]");
                if (meta != null) {
                    String imgUrl = meta.attr("content");
                    if (imgUrl.contains("lokmat-logo-metatgs.jpg")) {
                        System.out.println("Hit default logo for article " + articleId + ", breaking.");
                        break;
                    }
                    System.out.println("Found image for " + articleId + ": " + imgUrl);
                    imageUrls.add(imgUrl);
                } else {
                    System.out.println("No image meta found for " + articleId + ", breaking.");
                    break;
                }
            }
        } else {
            System.out.println("issueidPdf not found");
        }
    }
}
