package org.tablebuilder.demo.model;


import lombok.Data;
import java.util.List;

@Data
public class SheetInfo {
    private Long id;
    private String sheetName;
    private String originalSheetName;
    private String tableName;
    private int columnCount;
    private Long rowCount;
    private List<String> columns;

    public SheetInfo(Long id, String sheetName, String originalSheetName,
                     String tableName, int columnCount, Long rowCount) {
        this.id = id;
        this.sheetName = sheetName;
        this.originalSheetName = originalSheetName;
        this.tableName = tableName;
        this.columnCount = columnCount;
        this.rowCount = rowCount;
    }
}