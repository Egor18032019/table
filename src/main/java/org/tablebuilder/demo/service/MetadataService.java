package org.tablebuilder.demo.service;

import org.tablebuilder.demo.store.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MetadataService {

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
}