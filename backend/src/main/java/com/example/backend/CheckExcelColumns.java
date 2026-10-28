package com.example.backend;

import java.io.FileInputStream;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.DataFormatter;

public class CheckExcelColumns {
    public static void main(String[] args) throws Exception {
        String excelFile = "C:/Users/ACER/Desktop/stratup/link/All Newspapers.xlsx";
        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                System.out.println("=== SHEET: " + sheet.getSheetName() + " ===");
                DataFormatter formatter = new DataFormatter();
                org.apache.poi.ss.usermodel.FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                
                for (int r = 1; r <= 5; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        if (row.getCell(c) != null) {
                            String val = formatter.formatCellValue(row.getCell(c), evaluator);
                            if (!val.trim().isEmpty()) {
                                System.out.println("Row " + r + " Col " + c + ": " + val);
                            }
                        }
                    }
                }
            }
        }
    }
}
