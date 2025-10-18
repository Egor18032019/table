//package org.tablebuilder.demo.controllers;
//
//package org.tablebuilder.demo.controllers;
//
//import org.tablebuilder.demo.model.FileInfo;
//import org.tablebuilder.demo.model.SheetInfo;
//import org.tablebuilder.demo.service.MetadataService;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/metadata")
//@RequiredArgsConstructor
//@Tag(name = "Metadata Controller", description = "Контроллер для получения метаданных файлов и листов")
//public class MetadataController {
//
//    private final MetadataService metadataService;
//
//    @Operation(summary = "Получить список всех файлов")
//    @GetMapping("/files")
//    public ResponseEntity<List<FileInfo>> getAllFiles() {
//        try {
//            List<FileInfo> files = metadataService.getAllFiles();
//            return ResponseEntity.ok(files);
//        } catch (Exception e) {
//            return ResponseEntity.internalServerError().build();
//        }
//    }
//
//    @Operation(summary = "Получить информацию о конкретном файле")
//    @GetMapping("/files/{fileName}")
//    public ResponseEntity<FileInfo> getFileInfo(@PathVariable String fileName) {
//        try {
//            FileInfo fileInfo = metadataService.getFileInfo(fileName);
//            return ResponseEntity.ok(fileInfo);
//        } catch (Exception e) {
//            return ResponseEntity.notFound().build();
//        }
//    }
//
//    @Operation(summary = "Получить список всех листов файла")
//    @GetMapping("/files/{fileName}/sheets")
//    public ResponseEntity<List<SheetInfo>> getSheetsByFile(@PathVariable String fileName) {
//        try {
//            List<SheetInfo> sheets = metadataService.getSheetsByFile(fileName);
//            return ResponseEntity.ok(sheets);
//        } catch (Exception e) {
//            return ResponseEntity.notFound().build();
//        }
//    }
//
//    @Operation(summary = "Получить информацию о конкретном листе")
//    @GetMapping("/files/{fileName}/sheets/{sheetName}")
//    public ResponseEntity<SheetInfo> getSheetInfo(
//            @PathVariable String fileName,
//            @PathVariable String sheetName) {
//        try {
//            SheetInfo sheetInfo = metadataService.getSheetInfo(fileName, sheetName);
//            return ResponseEntity.ok(sheetInfo);
//        } catch (Exception e) {
//            return ResponseEntity.notFound().build();
//        }
//    }
//
//    @Operation(summary = "Получить список колонок листа")
//    @GetMapping("/files/{fileName}/sheets/{sheetName}/columns")
//    public ResponseEntity<List<String>> getSheetColumns(
//            @PathVariable String fileName,
//            @PathVariable String sheetName) {
//        try {
//            List<String> columns = metadataService.getSheetColumns(fileName, sheetName);
//            return ResponseEntity.ok(columns);
//        } catch (Exception e) {
//            return ResponseEntity.notFound().build();
//        }
//    }
//
//    @Operation(summary = "Поиск файлов по имени")
//    @GetMapping("/files/search")
//    public ResponseEntity<List<FileInfo>> searchFiles(@RequestParam String query) {
//        try {
//            List<FileInfo> files = metadataService.searchFiles(query);
//            return ResponseEntity.ok(files);
//        } catch (Exception e) {
//            return ResponseEntity.ok(List.of()); // возвращаем пустой список при ошибке
//        }
//    }
//
//    @Operation(summary = "Получить статистику по файлам")
//    @GetMapping("/stats")
//    public ResponseEntity<FileStats> getStats() {
//        try {
//            FileStats stats = metadataService.getFileStats();
//            return ResponseEntity.ok(stats);
//        } catch (Exception e) {
//            return ResponseEntity.internalServerError().build();
//        }
//    }
//}