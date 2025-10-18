package org.tablebuilder.demo.model;


import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FileInfo {
    private Long id;
    private String fileName;
    private String displayName;
    private String internalName;
    private String username;
    private LocalDateTime uploadDate;
    private int sheetCount;
    private Long totalRows;
    private List<SheetInfo> sheets;

    public FileInfo(Long id, String fileName, String displayName, String internalName,
                    String username, LocalDateTime uploadDate, int sheetCount, Long totalRows) {
        this.id = id;
        this.fileName = fileName;
        this.displayName = displayName;
        this.internalName = internalName;
        this.username = username;
        this.uploadDate = uploadDate;
        this.sheetCount = sheetCount;
        this.totalRows = totalRows;
    }
}