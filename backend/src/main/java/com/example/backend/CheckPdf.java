package com.example.backend;

import java.io.FileOutputStream;
import java.util.List;
import java.util.ArrayList;

public class CheckPdf {
    public static void main(String[] args) throws Exception {
        PdfService service = new PdfService();
        // Overwrite excel path for local test if needed, or just let it use EXCEL_PATH
        // We will just test with 1 link
        String targetDate = "2026-10-07"; // or 2026-10-03
        byte[] pdf = service.createPdfByDate("2026-10-03", "Lokmat", null, false);
        System.out.println("Generated PDF size: " + pdf.length);
        
        try (FileOutputStream fos = new FileOutputStream("test_output.pdf")) {
            fos.write(pdf);
        }
        System.out.println("Saved to test_output.pdf");
    }
}
