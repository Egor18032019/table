package org.tablebuilder.demo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.tablebuilder.demo.model.FileInfo;
import org.tablebuilder.demo.model.SheetInfo;
import org.tablebuilder.demo.store.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MetadataService {
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UploadedTableRepository uploadedTableRepository;

    @Autowired
    private TableColumnRepository tableColumnRepository;

    @Autowired
    private TableListRepository tableListRepository;

    /**
     * Сохраняем метаданные таблицы
     *
     * @param originalColumnNames - имена столбцов в файле
     * @param internalColumnNames - имена столбцов в БД
     */
    @Transactional
    public void saveTableMetadata(
            UploadedTable savedTable,
            List<String> originalColumnNames,
            List<String> internalColumnNames,
            String listName) {

        for (int i = 0; i < originalColumnNames.size(); i++) {
            TableColumn col = new TableColumn();
            col.setTable(savedTable);
            col.setInternalName(internalColumnNames.get(i));
            col.setDisplayName(originalColumnNames.get(i));
            col.setOriginalIndex(i);
            col.setListName(listName);
            tableColumnRepository.save(col);
        }
    }

    public void saveTableColumns(UploadedTable savedTable, List<String> originalColumnNames, List<String> internalColumnNames) {
        // Сохраняем столбцы
        for (int i = 0; i < originalColumnNames.size(); i++) {
            TableColumn col = new TableColumn();
            col.setTable(savedTable);
            col.setInternalName(internalColumnNames.get(i));
            col.setDisplayName(originalColumnNames.get(i));
            col.setOriginalIndex(i);
            tableColumnRepository.save(col);
        }
    }

    public UploadedTable saveUploadedTable(String originalTableName, String internalTableName, String username) {
        UploadedTable table = new UploadedTable();
        table.setInternalName(internalTableName);
        table.setDisplayName(originalTableName);
        table.setUsername(username);
        return uploadedTableRepository.save(table);
    }

    //todo написать 2 метода - по имени таблицы и по id таблицы  и второй по колонкам

    public UploadedTable getOriginalTableName(String internalTableName) {
        return uploadedTableRepository.findByInternalName(internalTableName)
                .orElseThrow(() -> new RuntimeException("Table not found: " + internalTableName));
    }

    public String getOriginalColumnName(Long tableId, String internalColumnName) {
        if (tableColumnRepository.existsByTableIdAndInternalName(tableId, internalColumnName)) {
            return tableColumnRepository.findByTableIdAndInternalName(tableId, internalColumnName)
                    .getDisplayName();
        } else {
            throw new RuntimeException("Column not found: " + internalColumnName);
        }

    }

    public void saveTableList(UploadedTable savedTable, String tableName,String originalListName) {
        TableList tableList = new TableList();
        tableList.setListName(tableName);
        tableList.setTable(savedTable);
        tableList.setOriginalListName(originalListName);
        tableListRepository.save(tableList);

    }

    /**
     * Получить список всех файлов
     */
    public List<FileInfo> getAllFiles() {
        try {
            List<UploadedTable> tables = uploadedTableRepository.findAllByOrderByCreatedAtDesc();

            return tables.stream()
                    .map(this::convertToFileInfo)
                    .collect(Collectors.toList());

        } catch (Exception e) {

            throw new RuntimeException("Failed to get files list", e);
        }
    }
    /**
     * Конвертация UploadedTable в FileInfo
     */
    private FileInfo convertToFileInfo(UploadedTable table) {
        try {
            List<TableList> tableLists = tableListRepository.findByTableId(table.getId());
            int sheetCount = tableLists.size();

            // Подсчет общего количества строк в файле
            Long totalRows = 0L;
            for (TableList tableList : tableLists) {
                try {
                    Long rowCount = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM " + tableList.getListName(), Long.class);
                    totalRows += rowCount != null ? rowCount : 0;
                } catch (Exception e) {
//                    log.warn("Error counting rows for table {}: {}", tableList.getListName(), e.getMessage());
                }
            }

            return new FileInfo(
                    table.getId(),
                    table.getDisplayName(),
                    table.getDisplayName(),
                    table.getInternalName(),
                    table.getUsername(),
                    table.getCreatedAt(),
                    sheetCount,
                    totalRows
            );

        } catch (Exception e) {
            // Возвращаем базовую информацию даже при ошибке
            return new FileInfo(
                    table.getId(),
                    table.getDisplayName(),
                    table.getDisplayName(),
                    table.getInternalName(),
                    table.getUsername(),
                    table.getCreatedAt(),
                    0,
                    0L
            );
        }
    }
    /**
     * Получить список колонок листа
     */
    public List<String> getSheetColumns(String fileName, String sheetName) {
        try {
            UploadedTable table = uploadedTableRepository.findByDisplayName(fileName);
            if (table == null) {
                throw new RuntimeException("File not found: " + fileName);
            }

            return tableColumnRepository.findByTableIdAndListNameOrderByOriginalIndex(table.getId(), sheetName)
                    .stream()
                    .map(TableColumn::getDisplayName)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to get columns", e);
        }
    }

    /**
     * Получить информацию о конкретном файле
     */
    public FileInfo getFileInfo(String fileName) {
        try {
            UploadedTable table = uploadedTableRepository.findByDisplayName(fileName);
            if (table == null) {
                throw new RuntimeException("File not found: " + fileName);
            }

            return convertToFileInfo(table);

        } catch (Exception e) {
            throw new RuntimeException("Failed to get file info", e);
        }
    }

    /**
     * Получить список всех листов файла
     */
    public List<SheetInfo> getSheetsByFile(String fileName) {
        try {
            UploadedTable table = uploadedTableRepository.findByDisplayName(fileName);
            if (table == null) {
                throw new RuntimeException("File not found: " + fileName);
            }

            List<TableList> tableLists = tableListRepository.findByTableId(table.getId());

            return tableLists.stream()
                    .map(tableList -> convertToSheetInfo(tableList, table.getId()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to get sheets", e);
        }
    }
    /**
     * Конвертация TableList в SheetInfo
     */
    private SheetInfo convertToSheetInfo(TableList tableList, Long tableId) {
        try {
            // Получаем колонки
            List<TableColumn> columns = tableColumnRepository.findByTableIdAndListNameOrderByOriginalIndex(tableId, tableList.getListName());
            int columnCount = columns.size();

            // Получаем количество строк
            Long rowCount = 0L;
            try {
                rowCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + tableList.getListName(), Long.class);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }

            SheetInfo sheetInfo = new SheetInfo(
                    tableList.getId(),
                    tableList.getListName(),
                    tableList.getOriginalListName(),
                    tableList.getListName(),
                    columnCount,
                    rowCount != null ? rowCount : 0L
            );

            // Добавляем названия колонок
            List<String> columnNames = columns.stream()
                    .map(TableColumn::getDisplayName)
                    .collect(Collectors.toList());
            sheetInfo.setColumns(columnNames);

            return sheetInfo;

        } catch (Exception e) {
            // Возвращаем базовую информацию даже при ошибке
            return new SheetInfo(
                    tableList.getId(),
                    tableList.getListName(),
                    tableList.getOriginalListName(),
                    tableList.getListName(),
                    0,
                    0L
            );
        }
    }

    /**
     * Получить информацию о конкретном листе
     */
    public SheetInfo getSheetInfo(String fileName, String sheetName) {
        try {
            UploadedTable table = uploadedTableRepository.findByDisplayName(fileName);
            if (table == null) {
                throw new RuntimeException("File not found: " + fileName);
            }

            TableList tableList = tableListRepository.findByTableIdAndOriginalListName(table.getId(), sheetName);
            if (tableList == null) {
                throw new RuntimeException("Sheet not found: " + sheetName + " in file: " + fileName);
            }

            return convertToSheetInfo(tableList, table.getId());

        } catch (Exception e) {
            throw new RuntimeException("Failed to get sheet info", e);
        }
    }
}