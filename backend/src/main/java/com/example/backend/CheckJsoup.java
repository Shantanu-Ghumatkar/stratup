package com.example.backend;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class CheckJsoup {
    public static void main(String[] args) throws Exception {
        String url = "http://epaper.lokmat.com/main-editions/Mumbai%20Main/2026-10-03/01";
        Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
        Elements scripts = doc.select("script");
        for (Element s : scripts) {
            if (s.hasAttr("src")) {
                System.out.println("SRC: " + s.attr("src"));
            } else {
                String html = s.html();
                if (html.contains("LOK_")) {
                    System.out.println("INLINE SCRIPT: " + html.substring(0, Math.min(200, html.length())));
                }
            }
        }
    }
}
