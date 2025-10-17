package org.tablebuilder.demo.model;

import lombok.Data;

import java.util.List;

@Data
public class TableRequest {
    private String tableName;
    private String listName;
    private List<FilterRequest> filters;
    private List<SortRequest> sorts;
    private int page = 0;
    private int size = 50;
}