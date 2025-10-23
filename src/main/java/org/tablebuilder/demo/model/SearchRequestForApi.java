package org.tablebuilder.demo.model;

import lombok.Data;

import java.util.List;

@Data
public class SearchRequestForApi {
    private List<FilterRequest> filters;
    private List<SortRequest> sorts;

}