package org.tablebuilder.demo.model;

import lombok.Data;

import java.util.List;

@Data
public class ListDTO {
    private String nameList;
    private List<ColumnDefinitionDTO> columns;
}
