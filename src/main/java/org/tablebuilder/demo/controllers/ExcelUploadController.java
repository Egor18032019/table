package org.tablebuilder.demo.controllers;

import org.springframework.web.bind.annotation.*;
import org.tablebuilder.demo.model.ExcelImportResult;
import org.tablebuilder.demo.service.ExcelImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/api/excel")
public class ExcelUploadController {

    @Autowired
    private ExcelImportService excelImportService;

    @PostMapping("/upload")
    public ResponseEntity<ExcelImportResult> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "username", defaultValue = "anonymous") String username) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ExcelImportResult(false, 0, "", "File is empty")
            );
        }

        // Проверка типа файла
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.toLowerCase().endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body(
                    new ExcelImportResult(false, 0, "", "Only .xlsx files are supported")
            );
        }

        try {
            ExcelImportResult result = excelImportService.importExcel(file, username);

            if (result.isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new ExcelImportResult(false, 0, "", "Validation error: " + e.getMessage())
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                    new ExcelImportResult(false, 0, "", "Import failed: " + e.getMessage())
            );
        }
    }
}