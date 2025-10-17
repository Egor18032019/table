package org.tablebuilder.demo.controllers;

import org.tablebuilder.demo.exception.InvalidNameException;
import org.tablebuilder.demo.model.TableTemplateDTO;
import org.tablebuilder.demo.service.DynamicTableService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/tables", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "DynamicTable Controller", description = "Контроллер для создания динамических таблиц")
public class DynamicTableController {


    private DynamicTableService dynamicTableService;

    @PostMapping("/create")
    public ResponseEntity<?> createTable(@RequestBody TableTemplateDTO template) {
        try {
            dynamicTableService.createTableFromTemplate(template);
            return ResponseEntity.ok().body("Table '" + template.getName() + "' created successfully.");
        } catch (InvalidNameException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to create table: " + e.getMessage());
        }
    }
}