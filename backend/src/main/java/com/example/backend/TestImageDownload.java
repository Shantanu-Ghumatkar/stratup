package com.example.backend;

import java.io.InputStream;
import java.nio.file.Files;
import java.net.URL;
import java.io.File;

public class TestImageDownload {
    public static void main(String[] args) {
        String src = "https://images.epaperlokmat.in/eNewspaper/News/LOK/MULK/2026/10/03/ArticleImages/6ac01df9729d4.jpg";
        File dest = new File("test_image.jpg");
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new URL(src).openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Downloaded image successfully! Size: " + dest.length());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
