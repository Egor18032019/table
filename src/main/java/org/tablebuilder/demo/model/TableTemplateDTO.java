package org.tablebuilder.demo.model;

import lombok.Data;

import java.util.List;

@Data
public class TableTemplateDTO {
    private String tableName;
    private List<ListDTO> columns;
}