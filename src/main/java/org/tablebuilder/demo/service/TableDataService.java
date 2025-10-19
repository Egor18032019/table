package org.tablebuilder.demo.service;

import org.tablebuilder.demo.model.*;
import org.tablebuilder.demo.store.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tablebuilder.demo.utils.NameUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableDataService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final UploadedTableRepository uploadedTableRepository;
    private final TableListRepository tableListRepository;
    private final TableColumnRepository tableColumnRepository;

    /**
     * Получить все строки с пагинацией
     */
    public PageableResponse<Map<String, Object>> getAllRows(String fileName, String sheetName,
                                                            int page, int size) {
        UploadedTable table = resolveTableName(fileName);
        TableList list_name = tableListRepository.findByTableIdAndOriginalListName(table.getId(), sheetName);
        // Получаем общее количество
        long totalCount = getTotalCount(list_name.getListName());

        // Получаем данные с пагинацией
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + list_name.getListName() + " ORDER BY id LIMIT ? OFFSET ?",
                size, page * size
        );

        // Преобразуем к правильным типам
        List<Map<String, Object>> typedRows = convertRowTypes(rows);
        return createPageableResponse(typedRows, page, size, totalCount);
    }

    /**
     * Получить строку по ID
     */
    public Map<String, Object> getRowById(String internalTableName, Long id) {


        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + internalTableName + " WHERE id = ?", id
        );

        if (rows.isEmpty()) {
            throw new RuntimeException("Row not found with id: " + id);
        }

        return convertRowTypes(rows.get(0));
    }

    /**
     * Создать новую строку
     */
    @Transactional
    public Map<String, Object> createRow(String fileName, String sheetName, Map<String, Object> cellData) {
        UploadedTable table = resolveTableName(fileName);
        TableList list_name = tableListRepository.findByTableIdAndOriginalListName(table.getId(), sheetName);
        String tableName = list_name.getListName();

        // Проверяем что таблица существует
        if (!tableExists(tableName)) {
            throw new RuntimeException("Table not found: " + tableName);
        }

        // Валидация входных данных
        if (cellData == null || cellData.isEmpty()) {
            throw new RuntimeException("Cell data cannot be empty");
        }

        // Получаем ВСЕ колонки таблицы (без id)
        List<String> allColumns = getTableColumns(tableName);

        // Создаем Map для всех значений строки
        Map<String, Object> rowData = new HashMap<>();

        // Заполняем значениями из запроса
        for (Map.Entry<String, Object> entry : cellData.entrySet()) {
            String columnName = entry.getKey();

            TableColumn foo = tableColumnRepository.findByDisplayName(columnName);
            String safeColumnName = foo.getInternalName();
            // Проверяем что колонка существует в таблице
            if (!allColumns.contains(safeColumnName)) {
                throw new RuntimeException("Column '" + columnName + "' not found in table. Available columns: " + allColumns);
            }

            // Преобразуем значение к правильному типу
            Object convertedValue = convertValueByType(entry.getValue());
            rowData.put(safeColumnName, convertedValue);
        }

        // Для колонок, не указанных в запросе, устанавливаем null
        for (String column : allColumns) {
            if (!rowData.containsKey(column)) {
                rowData.put(column, null);
            }
        }

        // Строим SQL запрос для ВСЕХ колонок
        String sql = buildInsertSqlForAllColumns(tableName, allColumns);
        Object[] values = getOrderedValues(allColumns, rowData);

        System.out.println("SQL: " + sql);
        System.out.println("Columns: " + allColumns);
        System.out.println("Values: " + Arrays.toString(values));

        // Выполняем запрос со всеми параметрами
        int affectedRows = jdbcTemplate.update(sql, values);
        if (affectedRows == 0) {
            throw new RuntimeException("Failed to insert row");
        }

        // Получаем ID последней вставленной строки
        Long generatedId = getLastInsertId(tableName);

        // Возвращаем созданную строку
        return getRowById(tableName, generatedId);
    }

    /**
     * Получить все колонки таблицы (без id)
     */
    private List<String> getTableColumns(String tableName) {
        try {
            List<String> columns = jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns " +
                            "WHERE table_name = ? AND column_name != 'id' " +
                            "ORDER BY ordinal_position",
                    String.class, tableName
            );
            return columns;
        } catch (Exception e) {
            System.err.println("Error getting table columns: " + e.getMessage());
            throw new RuntimeException("Cannot get table structure: " + e.getMessage());
        }
    }

    /**
     * Построение SQL запроса для ВСЕХ колонок
     */
    private String buildInsertSqlForAllColumns(String tableName, List<String> columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append("?, ".repeat(columns.size() - 1));
        sql.append("?)");
        return sql.toString();
    }

    /**
     * Получить значения в правильном порядке для SQL запроса
     */
    private Object[] getOrderedValues(List<String> columns, Map<String, Object> rowData) {
        Object[] values = new Object[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            values[i] = rowData.get(columns.get(i));
        }
        return values;
    }


    /**
     * Обновить строку
     */
    @Transactional
    public Map<String, Object> updateRow(String fileName, String sheetName, Long id,
                                         CellData cell) {
        UploadedTable table = resolveTableName(fileName);
        TableList list_name = tableListRepository.findByTableIdAndOriginalListName(table.getId(), sheetName);
        // Проверяем что таблица существует
        if (!tableExists(list_name.getListName())) {
            throw new RuntimeException("Table not found: " + list_name.getListName());
        }
        List<TableColumn> columns = tableColumnRepository.findByTableIdAndListNameAndDisplayName(table.getId(), list_name.getListName(), cell.getColumn());
        String internalColumnName = columns.getFirst().getInternalName();
        // Строим SQL запрос
        String sql = buildUpdateSql(list_name.getListName(), internalColumnName);
        System.out.println("UPDATE SQL: " + sql);
        System.out.println("Parameters: value=" + cell.getValue() + ", id=" + id);
        // Преобразуем значение к правильному типу
        Object value = convertValueForUpdate(cell.getValue(), internalColumnName, list_name.getListName());
        // Выполняем запрос с параметром
        int affectedRows = jdbcTemplate.update(sql, value, id);
        if (affectedRows == 0) {
            throw new RuntimeException("Failed to insert row");
        }

        // Возвращаем обновленную строку
        Map<String, Object> updatedRow = getRowById(list_name.getListName(), id);
        System.out.println("Updated row: " + updatedRow);
        System.out.println("=== END UPDATE ROW ===");

        return updatedRow;
    }


    /**
     * Удалить строку
     */
    @Transactional
    public void deleteRow(String tableName, String sheetName, Long id) {
        UploadedTable table = resolveTableName(tableName);
        TableList list_name = tableListRepository.findByTableIdAndOriginalListName(table.getId(), sheetName);
        // Проверяем что таблица существует
        if (!tableExists(list_name.getListName())) {
            throw new RuntimeException("Table not found: " + list_name.getListName());
        }

        int affectedRows = jdbcTemplate.update(
                "DELETE FROM " + list_name.getListName() + " WHERE id = ?", id
        );

        if (affectedRows == 0) {
            throw new RuntimeException("Row not found with id: " + id);
        }
    }

    /**
     * Удалить строку
     */
    @Transactional
    public void deleteRow(UploadedTable table, TableList list_name, Long id) {
        // Проверяем что таблица существует
        if (!tableExists(list_name.getListName())) {
            throw new RuntimeException("Table not found: " + list_name.getListName());
        }

        int affectedRows = jdbcTemplate.update(
                "DELETE FROM " + list_name.getListName() + " WHERE id = ?", id
        );

        if (affectedRows == 0) {
            throw new RuntimeException("Row not found with id: " + id);
        }
    }

    /**
     * Поиск строк с фильтрацией
     */
    public PageableResponse<Map<String, Object>> searchRows(String fileName, String sheetName,
                                                            SearchRequest searchRequest,
                                                            int page, int size) {

        UploadedTable table = resolveTableName(fileName);
        TableList list_name = tableListRepository.findByTableIdAndOriginalListName(table.getId(), sheetName);

        String tableName = list_name.getListName();

        // Строим запрос с фильтрацией
        StringBuilder sql = new StringBuilder("SELECT * FROM " + tableName);
        MapSqlParameterSource params = new MapSqlParameterSource();

        // Добавляем WHERE если есть фильтры
        if (searchRequest.getFilters() != null && !searchRequest.getFilters().isEmpty()) {
            sql.append(" WHERE ");
            buildFilterClause(sql, params, searchRequest.getFilters(), tableName);
        }

        // Добавляем ORDER BY если есть сортировка
        if (searchRequest.getSorts() != null && !searchRequest.getSorts().isEmpty()) {
            sql.append(" ORDER BY ");
            buildSortClause(sql, searchRequest.getSorts(), tableName);
        }

        // Добавляем пагинацию
        sql.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", size);
        params.addValue("offset", page * size);

        // Выполняем запрос
        List<Map<String, Object>> rows = namedParameterJdbcTemplate.queryForList(
                sql.toString(), params
        );

        // Получаем общее количество с учетом фильтров
        long totalCount = getFilteredCount(tableName, searchRequest.getFilters());

        List<Map<String, Object>> typedRows = convertRowTypes(rows);
        return createPageableResponse(typedRows, page, size, totalCount);
    }

//    /**
//     * Массовое создание строк
//     */
//    @Transactional
//    public BatchOperationResult createBatchRows(String fileName, String sheetName,
//                                                List<Map<String, Object>> rowsData) {
//        UploadedTable table = resolveTableName(fileName);
//        TableList list_name = tableListRepository.findByTableIdAndOriginalListName(table.getId(), sheetName);
//
//        String tableName = table.getInternalName();
//
//        BatchOperationResult result = new BatchOperationResult(0, 0, new ArrayList<>());
//
//        for (int i = 0; i < rowsData.size(); i++) {
//            try {
//                createRow(fileName, sheetName, rowsData.get(i));
//                result.setSuccessCount(result.getSuccessCount() + 1);
//            } catch (Exception e) {
//                result.setErrorCount(result.getErrorCount() + 1);
//                result.getErrors().add(new OperationError(
//                        i, e.getMessage(), rowsData.get(i)
//                ));
//                log.error("Failed to create row at index {}: {}", i, e.getMessage());
//            }
//        }
//
//        return result;
//    }

    /**
     * Массовое удаление строк
     */
    @Transactional
    public BatchOperationResult deleteBatchRows(String fileName, String sheetName, List<Long> ids) {
        UploadedTable table = resolveTableName(fileName);
        TableList list_name = tableListRepository.findByTableIdAndOriginalListName(table.getId(), sheetName);

        String tableName = list_name.getListName();

        BatchOperationResult result = new BatchOperationResult(0, 0, new ArrayList<>());

        for (int i = 0; i < ids.size(); i++) {
            try {
                deleteRow(table, list_name, ids.get(i));
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                result.setErrorCount(result.getErrorCount() + 1);
                result.getErrors().add(new OperationError(
                        i, e.getMessage(), ids.get(i)
                ));
                log.error("Failed to delete row with id {}: {}", ids.get(i), e.getMessage());
            }
        }

        return result;
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private UploadedTable resolveTableName(String fileName) {
        UploadedTable table = uploadedTableRepository.findByDisplayName(fileName);
        return table;
    }

    /**
     * Построение SQL запроса для INSERT
     */
    private String buildInsertSql(String tableName, String columnName) {
        return "INSERT INTO " + tableName + " (" + columnName + ") VALUES (?)";
    }


    /**
     * Построение SQL запроса для UPDATE
     */
    private String buildUpdateSql(String tableName, String columnName) {
        return "UPDATE " + tableName + " SET " + columnName + " = ? WHERE id = ?";
    }

    private boolean rowExists(String tableName, Long id) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE id = ?", Long.class, id
        );
        return count != null && count > 0;
    }

    private long getTotalCount(String tableName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Long.class
        );
        return count != null ? count : 0;
    }

    private long getFilteredCount(String tableName, List<FilterRequest> filters) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM " + tableName);
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (filters != null && !filters.isEmpty()) {
            sql.append(" WHERE ");
            buildFilterClause(sql, params, filters, tableName);
        }

        Long count = namedParameterJdbcTemplate.queryForObject(sql.toString(), params, Long.class);
        return count != null ? count : 0;
    }

    private void buildFilterClause(StringBuilder sql, MapSqlParameterSource params,
                                   List<FilterRequest> filters, String tableName) {
        for (int i = 0; i < filters.size(); i++) {
            if (i > 0) sql.append(" AND ");
            FilterRequest filter = filters.get(i);
            String paramName = "filter_" + i;

            switch (filter.getOperator().toLowerCase()) {
                case "equals":
                    sql.append(filter.getColumn()).append(" = :").append(paramName);
                    params.addValue(paramName, filter.getValue());
                    break;
                case "contains":
                    sql.append(filter.getColumn()).append(" ILIKE :").append(paramName);
                    params.addValue(paramName, "%" + filter.getValue() + "%");
                    break;
                case "gt":
                    sql.append(filter.getColumn()).append(" > :").append(paramName);
                    params.addValue(paramName, filter.getValue());
                    break;
                case "lt":
                    sql.append(filter.getColumn()).append(" < :").append(paramName);
                    params.addValue(paramName, filter.getValue());
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported operator: " + filter.getOperator());
            }
        }
    }

    private void buildSortClause(StringBuilder sql, List<SortRequest> sorts, String tableName) {
        for (int i = 0; i < sorts.size(); i++) {
            if (i > 0) sql.append(", ");
            SortRequest sort = sorts.get(i);
            String direction = "ASC".equalsIgnoreCase(sort.getDirection()) ? "ASC" : "DESC";
            sql.append(sort.getColumn()).append(" ").append(direction);
        }
    }

    private List<Map<String, Object>> convertRowTypes(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(this::convertRowTypes)
                .collect(Collectors.toList());
    }

    private Map<String, Object> convertRowTypes(Map<String, Object> row) {
        // Здесь можно добавить преобразование типов если нужно
        return new LinkedHashMap<>(row); // сохраняем порядок колонок
    }

    private <T> PageableResponse<T> createPageableResponse(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return new PageableResponse<>(
                content,
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                page >= totalPages - 1
        );
    }

    /**
     * Проверка существования таблицы
     */
    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = ?)",
                Boolean.class, tableName
        );
        return exists != null && exists;
    }

    /**
     * Получить ID последней вставленной строки
     */
    private Long getLastInsertId(String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM " + tableName + " ORDER BY id DESC LIMIT 1",
                Long.class
        );
    }

    /**
     * Получить тип колонки из БД
     */
    private String getColumnType(String tableName, String columnName) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT data_type FROM information_schema.columns " +
                            "WHERE table_name = ? AND column_name = ?",
                    String.class, tableName, columnName
            );
        } catch (Exception e) {
            System.err.println("Error getting column type: " + e.getMessage());
            return "text"; // по умолчанию
        }
    }

    /**
     * Преобразование значения для UPDATE
     */
    private Object convertValueForUpdate(Object value, String columnName, String tableName) {
        if (value == null) {
            return null;
        }

        try {
            // Получаем тип колонки из БД
            String columnType = getColumnType(tableName, columnName);
            System.out.println("Column " + columnName + " type: " + columnType);

            if (columnType != null) {
                switch (columnType.toLowerCase()) {
                    case "integer":
                    case "bigint":
                    case "smallint":
                        if (value instanceof Number) {
                            return ((Number) value).longValue();
                        } else {
                            return Long.parseLong(value.toString());
                        }

                    case "numeric":
                    case "decimal":
                    case "real":
                    case "double precision":
                        if (value instanceof Number) {
                            return ((Number) value).doubleValue();
                        } else {
                            return Double.parseDouble(value.toString());
                        }

                    case "boolean":
                    case "bool":
                        if (value instanceof Boolean) {
                            return value;
                        } else {
                            String strVal = value.toString().toLowerCase();
                            return "true".equals(strVal) || "1".equals(strVal) || "yes".equals(strVal) || "да".equals(strVal);
                        }

                    case "date":
                        // Обработка дат если нужно
                        return value.toString();

                    default: // text, varchar, etc.
                        return value.toString();
                }
            }

            // По умолчанию возвращаем как строку
            return value.toString();

        } catch (Exception e) {
            System.err.println("Error converting value '" + value + "' for column '" + columnName + "': " + e.getMessage());
            return value.toString(); // fallback
        }
    }

    /**
     * Преобразовать значение к типу колонки
     */
    private Object convertValueToColumnType(Object value, String columnType) {
        if (value == null) {
            return null;
        }

        try {
            if (columnType != null) {
                switch (columnType.toLowerCase()) {
                    case "integer":
                    case "bigint":
                    case "smallint":
                        if (value instanceof Number) {
                            return ((Number) value).longValue();
                        } else {
                            return Long.parseLong(value.toString());
                        }

                    case "numeric":
                    case "decimal":
                    case "real":
                    case "double precision":
                        if (value instanceof Number) {
                            return ((Number) value).doubleValue();
                        } else {
                            return Double.parseDouble(value.toString());
                        }

                    case "boolean":
                    case "bool":
                        if (value instanceof Boolean) {
                            return value;
                        } else {
                            String strVal = value.toString().toLowerCase();
                            return "true".equals(strVal) || "1".equals(strVal) || "yes".equals(strVal);
                        }

                    case "date":
                        if (value instanceof String) {
                            // Парсим дату из строки
                            return java.sql.Date.valueOf(value.toString());
                        }
                        break;

                    default: // text, varchar, etc.
                        return value.toString();
                }
            }

            // Если тип неизвестен, возвращаем как есть
            return value;

        } catch (Exception e) {
            System.err.println("Error converting value '" + value + "' to type '" + columnType + "': " + e.getMessage());
            return value; // возвращаем оригинальное значение при ошибке
        }
    }

    /**
     * Автоматическое преобразование значения по его типу
     */
    private Object convertValueByType(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number) {
            // Если число целое
            if (value instanceof Integer || value instanceof Long) {
                return ((Number) value).longValue();
            }
            // Если число с плавающей точкой
            if (value instanceof Double || value instanceof Float) {
                return ((Number) value).doubleValue();
            }
        }

        if (value instanceof Boolean) {
            return value;
        }

        // Для строк проверяем можно ли преобразовать в число
        if (value instanceof String) {
            String strValue = value.toString().trim();
            try {
                if (strValue.matches("-?\\d+")) { // целое число
                    return Long.parseLong(strValue);
                } else if (strValue.matches("-?\\d+\\.\\d+")) { // дробное число
                    return Double.parseDouble(strValue);
                } else if ("true".equalsIgnoreCase(strValue) || "false".equalsIgnoreCase(strValue)) {
                    return Boolean.parseBoolean(strValue);
                }
            } catch (Exception e) {
                // Если не удалось преобразовать, оставляем как строку
            }
        }

        // По умолчанию возвращаем как строку
        return value.toString();
    }
}