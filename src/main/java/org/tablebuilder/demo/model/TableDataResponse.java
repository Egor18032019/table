package org.tablebuilder.demo.model;

import lombok.Data;

import java.util.List;

@Data
public class TableDataResponse {
    private String tableName;        // Отображаемое имя: "Отчёты.xlsx (Продажи)"
    private List<String> columns;    // Оригинальные имена: ["№", "ФИО", "Сумма"]
    private List<List<String>> rows; // Данные как список списков строк
}