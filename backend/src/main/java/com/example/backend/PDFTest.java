package com.example.backend;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.io.IOUtils;
public class PDFTest {
    public static void main(String[] args) throws Exception {
        PDFMergerUtility merger = new PDFMergerUtility();
        merger.addSource(new RandomAccessReadBuffer(new byte[10]));
        merger.mergeDocuments(org.apache.pdfbox.io.IOUtils.createMemoryOnlyStreamCache());
    }
}
