package com.example.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    @Autowired
    private PdfService pdfService;

    @GetMapping("/sheets")
    public ResponseEntity<List<String>> getSheets() {
        try {
            List<String> sheets = pdfService.getExcelSheets();
            return new ResponseEntity<>(sheets, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/cities")
    public ResponseEntity<List<String>> getCities(@RequestParam(required = false) String sheetName) {
        try {
            List<String> cities = pdfService.getCitiesFromSheet(sheetName);
            return new ResponseEntity<>(cities, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/extracted-ids")
    public ResponseEntity<List<String>> getExtractedIds(
            @RequestParam String date, 
            @RequestParam(required = false) String sheetName,
            @RequestParam(required = false) String city) {
        try {
            List<String> ids = pdfService.getExtractedIdsByDate(date, sheetName, city);
            return new ResponseEntity<>(ids, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/by-date")
    public ResponseEntity<byte[]> createPdfByDate(
            @RequestParam String date, 
            @RequestParam(required = false) String sheetName,
            @RequestParam(required = false) String city,
            @RequestParam(required = false, defaultValue = "false") boolean extractAds) {
        try {
            if ("All".equalsIgnoreCase(sheetName)) {
                byte[] mergedPdfBytes = pdfService.createAllNewspapersPdfByDate(date, city, extractAds);
                if (mergedPdfBytes == null || mergedPdfBytes.length == 0) {
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDispositionFormData("filename", "All_News_" + date + ".pdf");
                return new ResponseEntity<>(mergedPdfBytes, headers, HttpStatus.OK);
            }

            byte[] pdfBytes = pdfService.createPdfByDate(date, sheetName, city, extractAds);
            if (pdfBytes == null || pdfBytes.length == 0) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename", "News_" + date + ".pdf");
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/by-date-range")
    public ResponseEntity<byte[]> createPdfsByDateRange(
            @RequestParam String startDate, 
            @RequestParam String endDate, 
            @RequestParam(required = false) String sheetName,
            @RequestParam(required = false) String city,
            @RequestParam(required = false, defaultValue = "false") boolean extractAds) {
        try {
            byte[] zipBytes = pdfService.createPdfsByDateRange(startDate, endDate, sheetName, city, extractAds);
            if (zipBytes == null || zipBytes.length == 0) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf("application/zip"));
            headers.setContentDispositionFormData("filename", "News_" + startDate + "_to_" + endDate + ".zip");
            return new ResponseEntity<>(zipBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/from-images")
    public ResponseEntity<byte[]> createPdfFromImages(@RequestParam("images") MultipartFile[] files) {
        try {
            List<File> tempFiles = new ArrayList<>();
            for (MultipartFile file : files) {
                File tempFile = File.createTempFile("upload_", ".jpg");
                file.transferTo(tempFile);
                tempFiles.add(tempFile);
            }
            byte[] pdfBytes = pdfService.createPdfFromImagesList(tempFiles);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename", "Images.pdf");
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
