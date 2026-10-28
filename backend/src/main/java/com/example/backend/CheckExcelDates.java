package com.example.backend;

import java.io.FileInputStream;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.DataFormatter;
import java.util.HashSet;
import java.util.Set;

public class CheckExcelDates {
    public static void main(String[] args) throws Exception {
        String excelFile = "C:/Users/ACER/Desktop/stratup/link/All Newspapers.xlsx";
        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
             
            Sheet sheet = workbook.getSheet("Lokmat");
            if (sheet == null) {
                System.out.println("Sheet not found!");
                return;
            }

            DataFormatter formatter = new DataFormatter();
            org.apache.poi.ss.usermodel.FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            Set<String> dates = new HashSet<>();
            for (Row row : sheet) {
                if (row.getCell(3) != null) {
                    String d = formatter.formatCellValue(row.getCell(3), evaluator).trim();
                    if (!d.isEmpty()) dates.add(d);
                }
            }
            System.out.println("Unique Dates in Excel: " + dates);
        }
    }
}
