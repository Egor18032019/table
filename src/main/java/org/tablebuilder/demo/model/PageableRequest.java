package org.tablebuilder.demo.model;

import lombok.Data;

@Data
public class PageableRequest {
    private int page = 0;
    private int size = 50;
    private String sortBy;
    private String sortDirection = "ASC";
}