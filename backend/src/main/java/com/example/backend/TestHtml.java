package com.example.backend;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class TestHtml {
    public static void main(String[] args) throws Exception {
        String url = "http://epaper.lokmat.com/main-editions/Mumbai%20Main/2026-10-03/01";
        Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
        System.out.println(doc.html());
    }
}
