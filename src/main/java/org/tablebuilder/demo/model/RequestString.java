package org.tablebuilder.demo.model;

import lombok.Data;

@Data
public class RequestString {
    String fileName;
    String sheetName;
    String columnName;
}
