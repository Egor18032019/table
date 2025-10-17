package org.tablebuilder.demo.model;

import lombok.Data;

@Data
public class SortRequest {
    private String column; // internalName столбца, например: "fio"
    //todo сделать enum
    private String direction; // "ASC" или "DESC"
}