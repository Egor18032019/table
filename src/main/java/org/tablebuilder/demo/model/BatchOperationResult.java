package org.tablebuilder.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResult {
    private int successCount;
    private int errorCount;
    private List<OperationError> errors;
}