package com.example.backend;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class TestJsoup {
    public static void main(String[] args) throws Exception {
        String url = "http://epaper.lokmat.com/main-editions/Ahilyanagar%20Main/2026-10-09/12";
        Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
        Elements boxes = doc.select("a.box");
        System.out.println("Boxes: " + boxes.size());
        if (!boxes.isEmpty()) {
            System.out.println("First box ID: " + boxes.first().attr("id"));
        }
        
        // Let's also look for any large images
        Elements images = doc.select("img");
        for (Element img : images) {
            String src = img.attr("src");
            if (src.contains("20261009")) {
                System.out.println("Image with 20261009: " + src);
            }
            if (src.toLowerCase().contains("page") || src.toLowerCase().contains("main")) {
                System.out.println("Page image candidate: " + src);
            }
        }
    }
}
