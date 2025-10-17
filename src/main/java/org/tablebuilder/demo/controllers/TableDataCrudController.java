package org.tablebuilder.demo.controllers;

import org.tablebuilder.demo.model.*;
import org.tablebuilder.demo.service.TableDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tables/data")
@RequiredArgsConstructor
@Tag(name = "Table Data CRUD Controller", description = "CRUD операции над данными таблиц")
public class TableDataCrudController {

    private final TableDataService tableDataService;

    @Operation(summary = "Получить все данные таблицы с пагинацией")
    @GetMapping("/{fileName}/rows")
    public ResponseEntity<PageableResponse<Map<String, Object>>> getAllRows(
            @PathVariable String fileName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String sheetName) {
        try {
            PageableResponse<Map<String, Object>> result = tableDataService.getAllRows(
                    fileName, sheetName, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Получить конкретную строку по ID")
    @GetMapping("/{fileName}/rows/{id}")
    public ResponseEntity<Map<String, Object>> getRowById(
            @PathVariable String fileName,
            @PathVariable Long id,
            @RequestParam(required = false) String sheetName) {
        try {
            Map<String, Object> row = tableDataService.getRowById(fileName, sheetName, id);
            return ResponseEntity.ok(row);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Создать новую строку")
    @PostMapping("/{fileName}/rows")
    public ResponseEntity<Map<String, Object>> createRow(
            @PathVariable String fileName,
            @RequestParam(required = false) String sheetName,
            @RequestBody Map<String, Object> rowData) {
        try {
            Map<String, Object> createdRow = tableDataService.createRow(fileName, sheetName, rowData);
            return ResponseEntity.ok(createdRow);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Обновить строку")
    @PutMapping("/{fileName}/rows/{id}")
    public ResponseEntity<Map<String, Object>> updateRow(
            @PathVariable String fileName,
            @PathVariable Long id,
            @RequestParam(required = false) String sheetName,
            @RequestBody Map<String, Object> rowData) {
        try {
            Map<String, Object> updatedRow = tableDataService.updateRow(fileName, sheetName, id, rowData);
            return ResponseEntity.ok(updatedRow);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Частичное обновление строки")
    @PatchMapping("/{fileName}/rows/{id}")
    public ResponseEntity<Map<String, Object>> partialUpdateRow(
            @PathVariable String fileName,
            @PathVariable Long id,
            @RequestParam(required = false) String sheetName,
            @RequestBody Map<String, Object> partialData) {
        try {
            Map<String, Object> updatedRow = tableDataService.partialUpdateRow(
                    fileName, sheetName, id, partialData);
            return ResponseEntity.ok(updatedRow);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Удалить строку")
    @DeleteMapping("/{fileName}/rows/{id}")
    public ResponseEntity<Void> deleteRow(
            @PathVariable String fileName,
            @PathVariable Long id,
            @RequestParam(required = false) String sheetName) {
        try {
            tableDataService.deleteRow(fileName, sheetName, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Поиск строк с фильтрацией")
    @PostMapping("/{fileName}/search")
    public ResponseEntity<PageableResponse<Map<String, Object>>> searchRows(
            @PathVariable String fileName,
            @RequestParam(required = false) String sheetName,
            @RequestBody SearchRequest searchRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            PageableResponse<Map<String, Object>> result = tableDataService.searchRows(
                    fileName, sheetName, searchRequest, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Массовое создание строк")
    @PostMapping("/{fileName}/batch")
    public ResponseEntity<BatchOperationResult> createBatchRows(
            @PathVariable String fileName,
            @RequestParam(required = false) String sheetName,
            @RequestBody List<Map<String, Object>> rowsData) {
        try {
            BatchOperationResult result = tableDataService.createBatchRows(
                    fileName, sheetName, rowsData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Массовое удаление строк")
    @DeleteMapping("/{fileName}/batch")
    public ResponseEntity<BatchOperationResult> deleteBatchRows(
            @PathVariable String fileName,
            @RequestParam(required = false) String sheetName,
            @RequestBody List<Long> ids) {
        try {
            BatchOperationResult result = tableDataService.deleteBatchRows(
                    fileName, sheetName, ids);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}