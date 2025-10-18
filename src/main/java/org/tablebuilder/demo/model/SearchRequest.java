package org.tablebuilder.demo.model;

import lombok.Data;

import java.util.List;

@Data
public class SearchRequest {
    private List<FilterRequest> filters;
    private List<SortRequest> sorts;

}