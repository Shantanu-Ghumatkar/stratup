package com.example.backend;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;

public class CheckExcel {
    public static void main(String[] args) throws Exception {
        String path = "c:/Users/ACER/Desktop/stratup/link/All Newspapers.xlsx";
        try (FileInputStream fis = new FileInputStream(new File(path));
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            
            System.out.println("Sheet name: " + sheet.getSheetName());
            for (int i = 0; i < Math.min(10, sheet.getLastRowNum() + 1); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String c3 = row.getCell(3) == null ? "null" : formatter.formatCellValue(row.getCell(3), evaluator);
                String c5 = row.getCell(5) == null ? "null" : formatter.formatCellValue(row.getCell(5), evaluator);
                System.out.println("Row " + i + " | Col 3 (D): " + c3 + " | Col 5 (F): " + c5);
            }
        }
    }
}
