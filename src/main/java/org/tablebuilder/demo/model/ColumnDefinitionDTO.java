package org.tablebuilder.demo.model;

import lombok.Data;

@Data
public class ColumnDefinitionDTO {
    private String name;
    private String type; // "STRING", "NUMBER", "DATE", "BOOLEAN" //todo Enum ?
    private boolean required;
    private boolean unique;

}