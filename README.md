# News PDF Generator 📰

A complete Full-Stack solution to automate the generation of PDF documents from dynamically fetched news links mapped via dates in an Excel spreadsheet. 

## 🛠️ Used Concepts & Technologies

### Backend (Java / Spring Boot)
1. **Spring Boot (v3.3.4)**: Used as the core framework to quickly bootstrap the REST API and handle dependency injection, application configuration, and Tomcat server embedding.
2. **Apache POI**: The standard Java library for parsing and reading Microsoft Office documents. Used here to parse `All Newspapers.xlsx`, locating user-specified dates in Column D and extracting corresponding article URLs from Column F.
3. **Jsoup**: A powerful HTML parser. It is used to execute HTTP GET requests against the article pages (e.g., Lokmat ePaper), scrape the DOM, and extract matching elements (`<a class="box">` tags and `<img>` tags).
4. **Apache PDFBox**: An open-source Java tool for creating and manipulating PDF documents. Used to dynamically create new PDF pages, calculate scaling factors for varying image sizes (to maintain aspect ratios), and render downloaded JPEG/PNG images securely onto the PDF canvas.
5. **ZipOutputStream**: Used in the date-range API to compress multiple generated PDFs into a single downloadable ZIP archive on the fly, directly in memory (`ByteArrayOutputStream`).
6. **Multipart File Handling**: Utilized for the third API to parse raw image data from `multipart/form-data` requests.

### Frontend (React / Vite)
1. **React.js**: Used for building the interactive user interface.
2. **Vite**: A modern frontend build tool that provides a faster and leaner development experience.
3. **Blob API & Object URLs**: Used to handle raw binary data (PDF/ZIP files) returned by the Java backend. The `window.URL.createObjectURL(blob)` concept allows us to trigger immediate, seamless file downloads inside the browser without navigating away.
4. **CSS Variables & Glassmorphism Aesthetics**: Used to craft a highly premium UI featuring a cohesive dark mode palette, smooth hover micro-animations, and styled form controls to create a beautiful user experience.

---

## 🆕 Recent Updates & Fixes

2. **Expanded ePaper Support**: Added specialized Jsoup scraper logic for **Lokmat**, **Loksatta**, **Pudhari**, **Punya Nagari**, and **Sakal**. 
   * **Reason**: Different publishers use entirely different DOM structures, APIs, and image metadata (`og:image` vs. JSON `level1` chunks vs. `.webp` endpoints vs. direct AWS bucket URL parsing). This enables robust fetching across multiple publishers.
2. **High-Resolution Image Stitching**: Developed custom algorithms to fetch and stitch high-resolution image chunks for **Sakal**, **Loksatta**, and **Pudhari**. 
   * **Reason**: Some epapers render low-resolution preview images by default and use a chunking system (2x2, 3x3 grids) or absolute-positioned high-res article overlays to render the page clearly on zoom. By downloading the individual chunks and assembling them seamlessly via `java.awt.Graphics2D` prior to PDF creation, the final PDF remains incredibly sharp and crisp.
3. **Lokmat Full-Page Extraction**: Rewrote Lokmat logic to hit its backend API (`getPageNumberDetails`) and parse High-Resolution JPEG endpoints directly from the AWS CloudFront/R2 bucket, completely bypassing the fragmented article blocks.
4. **Single Unified PDF for All Newspapers**: Upgraded the "All Newspapers" export functionality. Instead of bundling multiple PDFs into a `.zip` archive, the backend now utilizes Apache PDFBox `PDFMergerUtility` to dynamically stitch every selected newspaper and city into a single, seamless PDF document.
5. **AI/OCR Ad Extraction Integration (Placeholder)**: Integrated an "Extract Ads (using AI/OCR)" toggle on the frontend. The boolean parameter successfully passes through the API into the `PdfService.java` generator logic, paving the way for immediate integration with external Ad Extraction AI APIs.
6. **PDF Image Zoom & Clarity Fix**: Adjusted PDF physical page dimensions to scale down proportionately to standard A4 widths, while retaining original aspect ratios and preserving the native high-resolution image data.
   * **Reason**: Creating physical PDF pages matched 1:1 with massive pixel dimensions caused browser-based PDF viewers (like Chrome) to cap rendering memory. This caused the PDF to become blurry and unreadable when zoomed in. The new scaling prevents this and guarantees crisp, readable text upon zoom without introducing white borders.
7. **Hardened HTTP Fetching**: Injected `Mozilla/5.0` User-Agents into all `HttpURLConnection` and `Jsoup` connections, and verified `Content-Type` headers.
   * **Reason**: Several epaper servers actively blocked requests originating from default Java User-Agents with `403 Forbidden` responses.
8. **Local Maven Bundle**: Added a local Maven distribution inside the `backend/maven/` directory.
   * **Reason**: Streamlines execution across environments without mandating a global Maven system installation.
9. **UI Improvements**: All date inputs automatically default to today's date upon loading, providing a streamlined user experience.
10. **Extreme Multi-Threaded Performance Optimization**: Implemented a highly concurrent ThreadPool with `CompletableFuture` across all newspaper extraction algorithms (Lokmat, Sakal, Pudhari, Loksatta, Punya Nagari). 
   * **Reason**: Previously, background images and their numerous high-resolution article chunks were fetched and stitched sequentially, which was a massive bottleneck that resulted in several minutes of waiting. Now, *all pages* and *all their article overlays* are downloaded perfectly in parallel. This reduces total API response times from minutes to merely a few seconds.

---

## 🚀 How to Start the Project

### 1. Backend (Spring Boot)
Ensure you have **Java 17** installed.
1. Navigate to the `backend` directory.
2. Run the application using your IDE (Eclipse, IntelliJ IDEA, VS Code) by executing `BackendApplication.java`.
3. *Alternatively*, if you have Maven installed, run:
   ```bash
   mvn spring-boot:run
   ```
4. The server will start on `http://localhost:8080`.

### 2. Frontend (React)
Ensure you have **Node.js** installed.
1. Open a new terminal and navigate to the `frontend` directory:
   ```powershell
   cd c:\Users\ACER\Desktop\stratup\frontend
   ```
2. Start the Vite development server:
   ```bash
   npm run dev
   ```
3. Open your browser and navigate to the provided local address (typically `http://localhost:5173`).

---

## 🔌 API Documentation & Samples

All APIs are exposed under the base URL: `http://localhost:8080/api/pdf`

### 1. Create PDF By Date
*   **Endpoint**: `/by-date`
*   **Method**: `GET`
*   **Description**: Reads the Excel file for the given date, follows the matching links, extracts image IDs, downloads the article images, and returns a compiled PDF.
*   **Query Parameter**: `date` (String) e.g., `20261007`
*   **Sample cURL**:
    ```bash
    curl -X GET "http://localhost:8080/api/pdf/by-date?date=20261007" --output News_20261007.pdf
    ```

### 2. Create PDFs By Date Range
*   **Endpoint**: `/by-date-range`
*   **Method**: `GET`
*   **Description**: Processes multiple dates within a range and returns a ZIP file containing individual PDF documents for each processed date.
*   **Query Parameters**: `startDate` (String), `endDate` (String)
*   **Sample cURL**:
    ```bash
    curl -X GET "http://localhost:8080/api/pdf/by-date-range?startDate=20261001&endDate=20261007" --output News_Range.zip
    ```

### 3. Create PDF From Uploaded Images
*   **Endpoint**: `/from-images`
*   **Method**: `POST`
*   **Description**: Accepts an array of image files and returns a compiled PDF document.
*   **Form Data Parameter**: `images` (Array of Files)
*   **Sample cURL**:
    ```bash
    curl -X POST "http://localhost:8080/api/pdf/from-images" \
      -F "images=@image1.jpg" \
      -F "images=@image2.jpg" \
      --output Images.pdf
    ```
