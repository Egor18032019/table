package org.tablebuilder.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiSearchResponse {
    private boolean success;
    private String answer;
    private List<DataSource> dataSources;
    private String sqlQuery; // Для отладки
    private String error;

    @Data
    public static class DataSource {
        private String fileName;
        private String sheetName;
        private String description;
        private int relevanceScore; // 0-100
    }
}