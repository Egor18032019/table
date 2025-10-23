package org.tablebuilder.demo.controllers;

import org.tablebuilder.demo.model.*;
import org.tablebuilder.demo.service.TableDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.tablebuilder.demo.store.TableList;
import org.tablebuilder.demo.store.TableListRepository;
import org.tablebuilder.demo.store.UploadedTable;
import org.tablebuilder.demo.store.UploadedTableRepository;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
@RestController
@RequestMapping("/api/tables/data")
@RequiredArgsConstructor
@Tag(name = "Table Data CRUD Controller", description = "CRUD операции над данными таблиц")
public class TableDataCrudController {
    private final UploadedTableRepository uploadedTableRepository;
    private final TableListRepository tableListRepository;
    private final TableDataService tableDataService;

    @Operation(summary = "Получить все данные таблицы с пагинацией в запросе имя файла и имя листа")
    @GetMapping("/{fileName}/rows")
    public ResponseEntity<PageableResponse<Map<String, Object>>> getAllRows(
            @PathVariable String fileName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = true) String sheetName) {
        String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
        String decodedSheetName = sheetName != null
                ? URLDecoder.decode(sheetName, StandardCharsets.UTF_8)
                : null;
        try {
            PageableResponse<Map<String, Object>> result = tableDataService.getAllRows(
                    decodedFileName, decodedSheetName, page, size);
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
            @RequestParam String sheetName) {
        try {
            // Декодируем имя файла и листа из URL-encoding
            String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            String decodedSheetName = sheetName != null
                    ? URLDecoder.decode(sheetName, StandardCharsets.UTF_8)
                    : null;
            UploadedTable table = uploadedTableRepository.findByDisplayName(decodedFileName);
            TableList list_name = tableListRepository.findByTableIdAndOriginalListName(table.getId(), decodedSheetName);


            Map<String, Object> row = tableDataService.getRowById(list_name.getListName(), id);
            return ResponseEntity.ok(row);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Добавить в столбец новое значение (table3.xlsx)   ")
    @PostMapping("/{fileName}/rows")
    public ResponseEntity<Map<String, Object>> createRow(
            @PathVariable String fileName,
            @RequestParam(required = false) String sheetName,
            @RequestBody Map<String, Object> cell) {
        try {
            // Декодируем имя файла и листа из URL-encoding
            String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            String decodedSheetName = sheetName != null
                    ? URLDecoder.decode(sheetName, StandardCharsets.UTF_8)
                    : null;
            Map<String, Object> createdRow = tableDataService.createRow(decodedFileName, decodedSheetName, cell);
            return ResponseEntity.ok(createdRow);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Обновить ячейку")
    @PutMapping("/{fileName}/rows/{id}")
    public ResponseEntity<Map<String, Object>> updateRow(
            @PathVariable String fileName,
            @PathVariable Long id,
            @RequestParam String sheetName,
            @RequestBody CellData cell) {
        try {

            String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            String decodedSheetName = sheetName != null
                    ? URLDecoder.decode(sheetName, StandardCharsets.UTF_8)
                    : null;
            Map<String, Object> updatedRow = tableDataService.updateRow(decodedFileName, decodedSheetName, id, cell);
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
            String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            String decodedSheetName = sheetName != null
                    ? URLDecoder.decode(sheetName, StandardCharsets.UTF_8)
                    : null;
            tableDataService.deleteRow(decodedFileName, decodedSheetName, id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Фильтрация (equals,contains,gt,lt) и сортировка(ASC,DESC   ")
    @PostMapping("/{fileName}/search")
    public ResponseEntity<PageableResponse<Map<String, Object>>> searchRows(
            @PathVariable String fileName,
            @RequestParam String sheetName,
            @RequestBody SearchRequestForApi searchRequestForApi,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            String decodedSheetName = sheetName != null
                    ? URLDecoder.decode(sheetName, StandardCharsets.UTF_8)
                    : null;
            PageableResponse<Map<String, Object>> result = tableDataService.searchRows(
                    decodedFileName, decodedSheetName, searchRequestForApi, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

//    @Operation(summary = "Массовое создание строк")
//    @PostMapping("/{fileName}/batch")
//    public ResponseEntity<BatchOperationResult> createBatchRows(
//            @PathVariable String fileName,
//            @RequestParam(required = false) String sheetName,
//            @RequestBody List<Map<String, Object>> rowsData) {
//        try {
//            BatchOperationResult result = tableDataService.createBatchRows(
//                    fileName, sheetName, rowsData);
//            return ResponseEntity.ok(result);
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().build();
//        }
//    }

    @Operation(summary = "Массовое удаление строк")
    @DeleteMapping("/{fileName}/batch")
    public ResponseEntity<BatchOperationResult> deleteBatchRows(
            @PathVariable String fileName,
            @RequestParam(required = false) String sheetName,
            @RequestBody List<Long> ids) {
        try {
            String decodedFileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);
            String decodedSheetName = sheetName != null
                    ? URLDecoder.decode(sheetName, StandardCharsets.UTF_8)
                    : null;
            BatchOperationResult result = tableDataService.deleteBatchRows(
                    decodedFileName, decodedSheetName, ids);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}