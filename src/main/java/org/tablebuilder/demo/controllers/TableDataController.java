package org.tablebuilder.demo.controllers;

import org.tablebuilder.demo.model.*;
import org.tablebuilder.demo.service.ExcelExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/tables")
public class TableDataController {

    @Autowired
    private ExcelExportService excelExportService;

    @Operation(summary = "Возвращает данные таблицы с оригинальными именами столбцов по имени файла")
    @GetMapping("/{fileName}/data")
    public ResponseEntity<FileDataResponse> getFileDataOnPath(
            @PathVariable String fileName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            String decodedFileName = java.net.URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            FileDataResponse data = excelExportService.getFileData(decodedFileName, page, size);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @Operation(summary = "Возвращает данные таблицы с пагинацией, фильтрацией и сортировкой")
    @PostMapping("/file-data")
    public ResponseEntity<FileDataResponse> getFileData(@RequestBody TableRequest request) {
        try {
            FileDataResponse data = excelExportService.getFileData(request);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Получить значения колонки с пагинацией")
    @PostMapping("/data/columns")
    public ResponseEntity<PageableResponse<String>> getAllValueInColumn(
            @RequestBody RequestString request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        try {
            PageableResponse<String> result = excelExportService.getAllValueInColumn(
                    request.getFileName(),
                    request.getSheetName(),
                    request.getColumnName(),
                    page,
                    size
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Получить общее количество строк в таблице")
    @GetMapping("/{fileName}/count")
    public ResponseEntity<Long> getTotalRowCount(
            @PathVariable String fileName,
            @RequestParam(required = false) String sheetName) {
        try {
            String decodedFileName = java.net.URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            long count = excelExportService.getTotalRowCount(decodedFileName, sheetName);
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}