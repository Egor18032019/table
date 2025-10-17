package org.tablebuilder.demo.service;

import org.tablebuilder.demo.model.*;
import org.tablebuilder.demo.store.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
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
        String tableName = resolveTableName(fileName, sheetName);

        // Получаем общее количество
        long totalCount = getTotalCount(tableName);

        // Получаем данные с пагинацией
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + tableName + " ORDER BY id LIMIT ? OFFSET ?",
                size, page * size
        );

        // Преобразуем к правильным типам
        List<Map<String, Object>> typedRows = convertRowTypes(rows, tableName);

        return createPageableResponse(typedRows, page, size, totalCount);
    }

    /**
     * Получить строку по ID
     */
    public Map<String, Object> getRowById(String fileName, String sheetName, Long id) {
        String tableName = resolveTableName(fileName, sheetName);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + tableName + " WHERE id = ?", id
        );

        if (rows.isEmpty()) {
            throw new RuntimeException("Row not found with id: " + id);
        }

        return convertRowTypes(rows.get(0), tableName);
    }

    /**
     * Создать новую строку
     */
    @Transactional
    public Map<String, Object> createRow(String fileName, String sheetName, Map<String, Object> rowData) {
        String tableName = resolveTableName(fileName, sheetName);

        // Убираем ID если он есть (будет сгенерирован автоматически)
        rowData.remove("id");

        // Строим SQL запрос
        String sql = buildInsertSql(tableName, rowData);

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int paramIndex = 1;
            for (Object value : rowData.values()) {
                ps.setObject(paramIndex++, value);
            }
            return ps;
        }, keyHolder);

        // Получаем сгенерированный ID
        Long generatedId = keyHolder.getKeyAs(Long.class);

        // Возвращаем созданную строку
        return getRowById(fileName, sheetName, generatedId);
    }

    /**
     * Обновить строку
     */
    @Transactional
    public Map<String, Object> updateRow(String fileName, String sheetName, Long id,
                                         Map<String, Object> rowData) {
        String tableName = resolveTableName(fileName, sheetName);

        // Проверяем существование строки
        if (!rowExists(tableName, id)) {
            throw new RuntimeException("Row not found with id: " + id);
        }

        // Убираем ID из данных для обновления
        rowData.remove("id");

        // Строим SQL запрос
        String sql = buildUpdateSql(tableName, rowData, id);

        int affectedRows = jdbcTemplate.update(sql, rowData.values().toArray());

        if (affectedRows == 0) {
            throw new RuntimeException("Failed to update row with id: " + id);
        }

        // Возвращаем обновленную строку
        return getRowById(fileName, sheetName, id);
    }

    /**
     * Частичное обновление строки
     */
    @Transactional
    public Map<String, Object> partialUpdateRow(String fileName, String sheetName, Long id,
                                                Map<String, Object> partialData) {
        String tableName = resolveTableName(fileName, sheetName);

        // Получаем текущие данные
        Map<String, Object> currentData = getRowById(fileName, sheetName, id);

        // Объединяем с новыми данными
        partialData.forEach((key, value) -> {
            if (value != null) {
                currentData.put(key, value);
            }
        });

        // Обновляем
        return updateRow(fileName, sheetName, id, currentData);
    }

    /**
     * Удалить строку
     */
    @Transactional
    public void deleteRow(String tableName, String sheetName, Long id) {


        int affectedRows = jdbcTemplate.update(
                "DELETE FROM " + tableName + " WHERE id = ?", id
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
        String tableName = resolveTableName(fileName, sheetName);

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

        List<Map<String, Object>> typedRows = convertRowTypes(rows, tableName);
        return createPageableResponse(typedRows, page, size, totalCount);
    }

    /**
     * Массовое создание строк
     */
    @Transactional
    public BatchOperationResult createBatchRows(String fileName, String sheetName,
                                                List<Map<String, Object>> rowsData) {
        String tableName = resolveTableName(fileName, sheetName);

        BatchOperationResult result = new BatchOperationResult(0, 0, new ArrayList<>());

        for (int i = 0; i < rowsData.size(); i++) {
            try {
                createRow(fileName, sheetName, rowsData.get(i));
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                result.setErrorCount(result.getErrorCount() + 1);
                result.getErrors().add(new OperationError(
                        i, e.getMessage(), rowsData.get(i)
                ));
                log.error("Failed to create row at index {}: {}", i, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Массовое удаление строк
     */
    @Transactional
    public BatchOperationResult deleteBatchRows(String fileName, String sheetName, List<Long> ids) {
        String tableName = resolveTableName(fileName, sheetName);

        BatchOperationResult result = new BatchOperationResult(0, 0, new ArrayList<>());

        for (int i = 0; i < ids.size(); i++) {
            try {
                deleteRow(tableName, sheetName, ids.get(i));
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

    private String resolveTableName(String fileName, String sheetName) {
        UploadedTable table = uploadedTableRepository.findByDisplayName(fileName);
        if (sheetName != null) {
            TableList tableList = tableListRepository.findByTableIdAndOriginalListName(table.getId(), sheetName);
            return tableList.getListName();
        } else {
            // Возвращаем первый лист если sheetName не указан
            List<TableList> tableLists = tableListRepository.findByTableId(table.getId());
            return tableLists.get(0).getListName();
        }
    }

    private String buildInsertSql(String tableName, Map<String, Object> data) {
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();

        for (String column : data.keySet()) {
            if (columns.length() > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(column);
            placeholders.append("?");
        }

        return String.format("INSERT INTO %s (%s) VALUES (%s)",
                tableName, columns, placeholders);
    }

    private String buildUpdateSql(String tableName, Map<String, Object> data, Long id) {
        StringBuilder setClause = new StringBuilder();

        for (String column : data.keySet()) {
            if (setClause.length() > 0) {
                setClause.append(", ");
            }
            setClause.append(column).append(" = ?");
        }

        return String.format("UPDATE %s SET %s WHERE id = ?", tableName, setClause);
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

    private List<Map<String, Object>> convertRowTypes(List<Map<String, Object>> rows, String tableName) {
        return rows.stream()
                .map(row -> convertRowTypes(row, tableName))
                .collect(Collectors.toList());
    }

    private Map<String, Object> convertRowTypes(Map<String, Object> row, String tableName) {
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
}