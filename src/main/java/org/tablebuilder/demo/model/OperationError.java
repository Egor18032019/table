package org.tablebuilder.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public   class OperationError {
    private int index;
    private String message;
    private Object data;
}