package org.tablebuilder.demo.model;

import lombok.Data;

@Data
public class AiSearchRequest {
    private String query;
    private String username;
}