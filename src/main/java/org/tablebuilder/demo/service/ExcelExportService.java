package org.tablebuilder.demo.service;

import org.tablebuilder.demo.model.*;
import org.tablebuilder.demo.store.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class ExcelExportService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UploadedTableRepository uploadedTableRepository;

    @Autowired
    private TableListRepository tableListRepository;

    @Autowired
    private TableColumnRepository tableColumnRepository;

    /**
     * Получить данные таблицы с пагинацией
     */
    public List<List<String>> getTableData(List<String> internalColumnNames, String listName,
                                           List<FilterRequest> filters,
                                           List<SortRequest> sorts,
                                           int page, int size) {
        String selectColumns = String.join(", ", internalColumnNames);
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(selectColumns);
        sql.append(" FROM ").append(listName);

        // WHERE (фильтрация)
        List<Object> params = new ArrayList<>();
        if (filters != null && !filters.isEmpty()) {
            sql.append(" WHERE ");
            for (int i = 0; i < filters.size(); i++) {
                if (i > 0) sql.append(" AND ");
                String col = filters.get(i).getColumn();
                String internalCol = tableColumnRepository.findByDisplayNameAndListName(col, listName).getInternalName();
                buildFilterClause(sql, filters.get(i), internalCol, params);
            }
        }

        // ORDER BY (сортировка)
        if (sorts != null && !sorts.isEmpty()) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < sorts.size(); i++) {
                if (i > 0) sql.append(", ");
                SortRequest sort = sorts.get(i);
                String internalCol = tableColumnRepository.findByDisplayNameAndListName(sort.getColumn(), listName).getInternalName();
                String dir = "ASC".equalsIgnoreCase(sort.getDirection()) ? "ASC" : "DESC";
                sql.append(internalCol).append(" ").append(dir);
            }
        }

        // LIMIT и OFFSET для пагинации
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        System.out.println("SQL with pagination: " + sql);

        List<List<String>> rows = jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            List<String> row = new ArrayList<>();
            for (String col : internalColumnNames) {
                row.add(rs.getString(col));
            }
            return row;
        });
        return rows;
    }



    @Transactional
    public FileDataResponse getFileData(String fileName, int page, int size) {
        FileDataResponse response = new FileDataResponse();
        response.setFileName(fileName);
        List<SheetData> sheets = new ArrayList<>();

        UploadedTable table = uploadedTableRepository.findByDisplayName(fileName);
        List<TableList> tableLists = tableListRepository.findByTableId(table.getId());

        for (TableList tableList : tableLists) {
            List<TableColumn> columns = tableColumnRepository.findByTableIdAndListNameOrderByOriginalIndex(
                    table.getId(), tableList.getListName());
            List<String> displayColumnNames = columns.stream()
                    .map(TableColumn::getDisplayName)
                    .collect(Collectors.toList());
            List<String> internalColumnNames = columns.stream()
                    .map(TableColumn::getInternalName)
                    .collect(Collectors.toList());

            SheetData sheetData = new SheetData();
            String sheetName = tableList.getOriginalListName();
            sheetData.setSheetName(sheetName);
            sheetData.setColumns(displayColumnNames);

            // Получаем общее количество строк
            long totalRows = getTotalRowCount(tableList.getListName(), new ArrayList<>());

            // Запрашиваем данные с пагинацией
            List<List<String>> rows = getTableData(internalColumnNames, tableList.getListName(),
                    null, null, page, size);

            sheetData.setRows(rows);
            sheets.add(sheetData);

            // Устанавливаем информацию о пагинации
            response.setPagination(new FileDataResponse.PaginationInfo(page, size, totalRows));
        }

        response.setSheets(sheets);
        return response;
    }

    @Transactional
    public FileDataResponse getFileData(TableRequest request) {
        String fileName = request.getTableName();
        FileDataResponse response = new FileDataResponse();
        response.setFileName(fileName);
        List<SheetData> sheets = new ArrayList<>();

        UploadedTable table = uploadedTableRepository.findByDisplayName(fileName);
        List<TableList> tableLists = tableListRepository.findByTableId(table.getId());

        for (TableList tableList : tableLists) {
            List<TableColumn> columns = tableColumnRepository.findByTableIdAndListNameOrderByOriginalIndex(
                    table.getId(), tableList.getListName());
            List<String> displayColumnNames = columns.stream()
                    .map(TableColumn::getDisplayName)
                    .collect(Collectors.toList());
            List<String> internalColumnNames = columns.stream()
                    .map(TableColumn::getInternalName)
                    .collect(Collectors.toList());

            SheetData sheetData = new SheetData();
            String sheetName = tableList.getOriginalListName();
            sheetData.setSheetName(sheetName);
            sheetData.setColumns(displayColumnNames);

            List<List<String>> rows;
            if (request.getListName().equals(sheetName)) {
                List<FilterRequest> filters = request.getFilters();
                List<SortRequest> sorts = request.getSorts();
                int page = request.getPage();
                int size = request.getSize();

                // Получаем общее количество с учетом фильтров
                long totalRows = getTotalRowCount(tableList.getListName(), filters);
                rows = getTableData(internalColumnNames, tableList.getListName(), filters, sorts, page, size);

                // Устанавливаем пагинацию
                response.setPagination(new FileDataResponse.PaginationInfo(page, size, totalRows));
            } else {
                // Для других листов без пагинации (или с пагинацией по умолчанию)
                rows = getTableData(internalColumnNames, tableList.getListName(), null, null, 0, 50);
                long totalRows = getTotalRowCount(tableList.getListName(), new ArrayList<>());
                response.setPagination(new FileDataResponse.PaginationInfo(0, 50, totalRows));
            }

            sheetData.setRows(rows);
            sheets.add(sheetData);
        }

        response.setSheets(sheets);
        System.out.println(response);
        return response;
    }

    /**
     * Получить значения колонки с пагинацией
     */
    public PageableResponse<String> getAllValueInColumn(String fileName, String sheetName,
                                                        String columnName, int page, int size) {
        SheetData sheetData = getSheetByOriginalName(fileName, sheetName);
        List<String> columns = sheetData.getColumns();

        // Находим индекс колонки
        int columnIndex = IntStream.range(0, columns.size()).filter(i -> columns.get(i).equals(columnName)).findFirst().orElse(-1);

        if (columnIndex == -1) {
            return new PageableResponse<>(List.of(), page, size, 0, 0, true, true);
        }

        // Применяем пагинацию к данным
        List<String> allValues = sheetData.getRows().stream()
                .map(row -> row.get(columnIndex))
                .collect(Collectors.toList());

        long totalElements = allValues.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int start = Math.min(page * size, allValues.size());
        int end = Math.min(start + size, allValues.size());

        List<String> pageContent = allValues.subList(start, end);

        return new PageableResponse<>(
                pageContent,
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                page >= totalPages - 1
        );
    }

    /**
     * Получить общее количество строк в таблице
     */
    public long getTotalRowCount(String fileName, String sheetName) {
        UploadedTable table = uploadedTableRepository.findByDisplayName(fileName);
        if (sheetName != null) {
            TableList tableList = tableListRepository.findByTableIdAndOriginalListName(table.getId(), sheetName);
            if (tableList != null) {
                return getTotalRowCount(tableList.getListName(), new ArrayList<>());
            }
        }

        // Если sheetName не указан, возвращаем сумму по всем листам
        List<TableList> tableLists = tableListRepository.findByTableId(table.getId());
        long total = 0;
        for (TableList tableList : tableLists) {
            total += getTotalRowCount(tableList.getListName(), new ArrayList<>());
        }
        return total;
    }
    /**
     * Получить общее количество строк
     */
    public long getTotalRowCount(String listName, List<FilterRequest> filters) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(listName);
        List<Object> params = new ArrayList<>();

        if (filters != null && !filters.isEmpty()) {
            sql.append(" WHERE ");
            for (int i = 0; i < filters.size(); i++) {
                if (i > 0) sql.append(" AND ");
                String col = filters.get(i).getColumn();
                String internalCol = tableColumnRepository.findByDisplayNameAndListName(col, listName).getInternalName();
                buildFilterClause(sql, filters.get(i), internalCol, params);
            }
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Long.class);
        return count != null ? count : 0L;
    }

    public SheetData getSheetByOriginalName(String decodedFileName, String sheetName) {
        FileDataResponse fileDataResponse = getFileData(decodedFileName, 0, Integer.MAX_VALUE);
        List<SheetData> sheets = fileDataResponse.getSheets();
        Optional<SheetData> sheetData = sheets.stream()
                .filter(sheet -> sheet.getSheetName().equals(sheetName))
                .findFirst();

        return sheetData.orElse(new SheetData());
    }

    private void buildFilterClause(StringBuilder sql, FilterRequest filter, String col, List<Object> params) {
        String op = filter.getOperator();
        String value = filter.getValue();

        switch (op) {
            case "contains" -> {
                sql.append(col).append(" ILIKE ?");
                params.add("%" + value + "%");
            }
            case "equals" -> {
                sql.append(col).append(" = ?");
                params.add(value);
            }
            case "gt" -> {
                sql.append(col).append(" > ?");
                params.add(value);
            }
            case "lt" -> {
                sql.append(col).append(" < ?");
                params.add(value);
            }
            case "gte" -> {
                sql.append(col).append(" >= ?");
                params.add(value);
            }
            case "lte" -> {
                sql.append(col).append(" <= ?");
                params.add(value);
            }
            case "between" -> {
                sql.append(col).append(" BETWEEN ? AND ?");
                params.add(value);
                params.add(filter.getValue2());
            }
            default -> throw new IllegalArgumentException("Unsupported operator: " + op);
        }
    }
}