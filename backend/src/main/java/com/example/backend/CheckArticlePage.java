package com.example.backend;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class CheckArticlePage {
    public static void main(String[] args) throws Exception {
        for (int i = 1; i <= 3; i++) {
            String articleId = "LOK_MULK_20261003_1_" + i;
            String url = "https://epaper.lokmat.com/articlepage.php?articleid=" + articleId;
            try {
                Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
                Element meta = doc.selectFirst("meta[property=og:image]");
                System.out.println(articleId + " -> " + (meta != null ? meta.attr("content") : "NOT FOUND"));
            } catch (Exception e) {
                System.out.println(articleId + " -> ERROR " + e.getMessage());
            }
        }
    }
}
