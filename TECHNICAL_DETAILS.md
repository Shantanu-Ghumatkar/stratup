# Technical Architecture & Changes Report 
**Date**: October 8, 2026
**Focus**: E-Paper High-Resolution Image Stitching and PDF Quality Improvements

## 1. Executive Summary
The primary issue addressed in this update was the severe blurring and pixelation of generated PDFs for three major newspapers: **Sakal**, **Loksatta**, and **Pudhari**. 

When zooming into the generated PDFs, the text was entirely unreadable. This occurred because the original system logic blindly scraped the default "preview" images provided by these publishers' APIs, which are typically capped at low resolutions (e.g., 600x964 pixels) to save bandwidth on mobile devices.

To resolve this, the system was completely refactored to bypass the low-res previews. It now actively queries for high-resolution chunks or article-level image data, downloads all necessary high-res components, and programmatically stitches them together into massive, seamless `BufferedImage` canvases using Java's `Graphics2D` library before passing them to Apache PDFBox.

---

## 2. Technical Details by Publisher

### A. Sakal
*   **The Old Code / Issue**: The previous script was intercepting the initial `.jpg` response which served a highly compressed, low-quality thumbnail representation of the page.
*   **The New Logic**: Sakal implements a grid-based tiling system for zoom. The page is broken down into a 3x3 grid (9 chunks). 
*   **Implementation**: 
    1. The code extracts the base URL and dynamically strips the filename.
    2. It loops through rows (1-3) and columns (1-3), appending `{col}x{row}.jpg`.
    3. It downloads all 9 high-res chunks.
    4. A master `BufferedImage` is created based on the total calculated width and height.
    5. The chunks are drawn onto the canvas at `X = (col-1) * chunkWidth` and `Y = (row-1) * chunkHeight`.

### B. Loksatta
*   **The Old Code / Issue**: The Jsoup parser looked at the `levels` object in the JSON response but defaulted to `leveldefault` (a 600x964 preview image).
*   **The New Logic**: Loksatta's API provides multiple resolution tiers. The highest quality tier (`level3`) contains a 2x2 grid of 1200x1927 chunks.
*   **Implementation**: 
    1. The code inspects the `levels` JSON array and prioritizes `level3` > `level2` > `level1`.
    2. It parses the `chunks` array within the optimal level, isolating `.jpg` files.
    3. Using the explicitly provided `tx` and `ty` coordinates from the JSON, the images are precisely drawn onto a dynamically sized master canvas (typically resolving to a crisp 2400x3854 pixel page).

### C. Pudhari
*   **The Old Code / Issue**: The system fetched the `fullImagePath` from the API, which returned a 150KB, 600x936 pixel thumbnail. Zooming in on this caused severe artifacting.
*   **The New Logic**: Pudhari uses a highly complex hybrid rendering system. It provides a low-resolution base background, but maps high-resolution cropped images for *every individual article* via `x1`, `y1` coordinates. Furthermore, the default `www.enewspapr.com` endpoints returned `404 Not Found` for Java `HttpURLConnection` agents, requiring domain substitution.
*   **Implementation**: 
    1. The code first downloads the low-res background image and scales it up uniformly by a factor of 3 (resulting in an 1800x2808 canvas).
    2. It iterates through the `Articles` JSON object for the page.
    3. It maps `imagename` paths, overriding the faulty `www.enewspapr.com` domain with the working `images.enewspapr.com` equivalent (sourced from `r2imagename`).
    4. Each high-res article overlay is downloaded and drawn directly onto the scaled background canvas exactly at `(x1 * 3, y1 * 3)` with dimensions `(w * 3, h * 3)`.
    5. This hybrid overlay strategy perfectly reconstructs a crisp, readable page without forcing the system to download multi-megabyte PDFs.

### D. Lokmat
*   **The Old Code / Issue**: The previous script targeted article-specific DOM endpoints (`articlepage.php?articleid=...`). This fragmented the newspaper, downloading individually cropped rectangular components of articles instead of full pages, ruining the reading experience.
*   **The New Logic**: Similar to Pudhari, Lokmat provides a low-resolution base background, and maps high-resolution cropped images for every individual article. The parser intercepts `getPageNumberDetails` and `getPageArticleDetails` APIs to fetch background and article metadata.
*   **Implementation**: 
    1. The code extracts page metadata and downloads the low-res background image, scaling it up uniformly by a factor of 3.
    2. It iterates through the `Articles` JSON object for the page.
    3. Each high-res article overlay is downloaded and drawn directly onto the scaled background canvas exactly at `(x1 * 3, y1 * 3)` with dimensions `(w * 3, h * 3)`.
    4. This hybrid overlay perfectly reconstructs a crisp, readable page without forcing the system to download multi-megabyte PDFs.

---

## 3. Load Balancing & Performance Architecture
*   **The Problem**: Processing up to 10 cities for "All Newspapers" generated enormous memory loads (processing 1800x2800 pixel images concurrently), leading to garbage collection thrashing and multi-minute wait times.
*   **The Solution**: A Master-Worker architecture was implemented on localhost.
*   **Implementation**:
    1. A powershell script (`start-workers.ps1`) spins up 3 independent Spring Boot JVMs on ports `8081` (Sakal), `8082` (Lokmat), and `8083` (Pudhari).
    2. The Master node (`8080`) intercepts requests for "All".
    3. It dispatches HTTP GET requests to the workers in parallel, completely isolating CPU and RAM loads per newspaper.
    4. The Master node merges the resulting PDFs upon completion, reducing time from 8 minutes down to a fraction of the time.

---

## 4. Concurrency Deadlock Resolution
*   **The Issue**: When attempting to fetch multiple cities concurrently using Java's `parallelStream()`, the network completely froze and yielded `SocketTimeoutException`.
*   **Root Cause**: Nested Thread Pools. The same `ExecutorService` was used for processing top-level "Pages" and bottom-level "Article Chunks". The top-level pages consumed all available threads, preventing the chunks from being downloaded, leading to an application-wide deadlock.
*   **Implementation**:
    1. Strict Thread Pool Separation: Implemented distinct `pageExecutor` (max 4 threads) and `articleExecutor` (max 32 threads).
    2. For Sakal, `ForkJoinPool.commonPool()` was abandoned in favor of a dedicated `FixedThreadPool(8)`.
    3. This effectively acts as a network throttle, preventing OS-level TCP starvation and HTTP timeouts.

---

## 5. Ad Extraction Integration Strategy
*   **Current State**: The frontend provides an "Extract Ads" checkbox that sets `extractAds=true`. The `PdfController` routes this to the `PdfService`.
*   **AI/OCR Integration Point**: A placeholder branch inside the PDF generation loop logs the request. This represents the precise injection point for external AI/OCR endpoints to run inference on the raw `byte[]` arrays or `BufferedImage` objects before they are converted into PDFs.

---

## 6. General Enhancements
*   **File I/O Offloading**: All stitched images are temporarily written to the local disk (`/temp` directory) using `javax.imageio.ImageIO.write` rather than being held entirely in RAM. This prevents heap space `OutOfMemoryError` exceptions.
*   **Graceful Fallbacks**: Every stitching algorithm is wrapped in a robust `try/catch` block. If a server goes offline, a domain changes, or an article overlay 404s, the system logs a `WARN` but gracefully falls back to the low-resolution preview image.
*   **Security & Agent spoofing**: Added `Mozilla/5.0` User-Agent strings to all raw `HttpURLConnection` requests, preventing `403 Forbidden` firewall blocks.
