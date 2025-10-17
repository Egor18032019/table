package org.tablebuilder.demo.model;

import lombok.Data;

import java.util.List;

@Data
public class SheetData {
    private String sheetName;        // "Продажи"
    private List<String> columns;    // ["№", "ФИО", "Сумма"]
    private List<List<String>> rows; // данные
}