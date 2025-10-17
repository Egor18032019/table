package org.tablebuilder.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class BatchInsertService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    // Оптимальные размеры пачек для разных СУБД
    private static final int POSTGRES_BATCH_SIZE = 1000;
    private static final int MYSQL_BATCH_SIZE = 2000;
    private static final int ORACLE_BATCH_SIZE = 500;

    // Текущий размер пачки (можно настраивать)
    private int currentBatchSize = POSTGRES_BATCH_SIZE;

    /**
     * Высокопроизводительная пакетная вставка данных
     */
    @Transactional
    public BatchInsertResult batchInsert(String tableName, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return new BatchInsertResult(0, 0, Collections.emptyList());
        }

        long startTime = System.currentTimeMillis();
        List<InsertError> errors = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        try {
            // Получаем имена колонок из первой строки
            List<String> columns = new ArrayList<>(rows.get(0).keySet());

            // Оптимизация: проверяем нужно ли экранировать имена колонок
            List<String> safeColumns = columns.stream()
                    .map(this::escapeColumnName)
                    .collect(Collectors.toList());

            // Используем multi-value INSERT для лучшей производительности
            if (isMultiValueInsertSupported()) {
                processWithMultiValueInsert(tableName, safeColumns, rows, successCount, errors);
            } else {
                processWithBatchInsert(tableName, safeColumns, rows, successCount, errors);
            }

            long endTime = System.currentTimeMillis();
            System.out.printf("Batch insert completed: %d success, %d errors, time: %dms%n",
                    successCount.get(), errors.size(), (endTime - startTime));

            return new BatchInsertResult(successCount.get(), errors.size(), errors);

        } catch (Exception e) {
            System.err.println("Batch insert failed: " + e.getMessage());
            return new BatchInsertResult(successCount.get(), rows.size() - successCount.get(), errors);
        }
    }

    /**
     * Multi-value INSERT (один запрос с множеством VALUES) - самый быстрый метод
     */
    private void processWithMultiValueInsert(String tableName, List<String> columns,
                                             List<Map<String, Object>> rows,
                                             AtomicInteger successCount, List<InsertError> errors) {

        // Разбиваем на пачки для избежания ограничений SQL
        for (int i = 0; i < rows.size(); i += currentBatchSize) {
            int end = Math.min(i + currentBatchSize, rows.size());
            List<Map<String, Object>> batch = rows.subList(i, end);

            try {
                String sql = buildMultiValueInsertSQL(tableName, columns, batch.size());
                List<Object> allValues = flattenBatchValues(columns, batch);

                int[] results = jdbcTemplate.batchUpdate(sql,
                        new MultiValueBatchPreparedStatementSetter(columns, batch));

                // Считаем успешные вставки
                int batchSuccess = Arrays.stream(results).sum();
                successCount.addAndGet(batchSuccess);

            } catch (Exception e) {
                // При ошибке пачки, пробуем вставить по одному
                processBatchIndividually(tableName, columns, batch, i, successCount, errors, e);
            }
        }
    }

    /**
     * Классический batch insert (менее производительный, но более надежный)
     */
    private void processWithBatchInsert(String tableName, List<String> columns,
                                        List<Map<String, Object>> rows,
                                        AtomicInteger successCount, List<InsertError> errors) {

        String sql = buildInsertSQL(tableName, columns);

        // Разбиваем на пачки
        for (int i = 0; i < rows.size(); i += currentBatchSize) {
            int end = Math.min(i + currentBatchSize, rows.size());
            List<Map<String, Object>> batch = rows.subList(i, end);

            try {
                int[] results = jdbcTemplate.batchUpdate(sql,
                        new SimpleBatchPreparedStatementSetter(columns, batch));

                int batchSuccess = Arrays.stream(results).sum();
                successCount.addAndGet(batchSuccess);

            } catch (Exception e) {
                processBatchIndividually(tableName, columns, batch, i, successCount, errors, e);
            }
        }
    }

    /**
     * Обработка пачки по одному при ошибке batch
     */
    private void processBatchIndividually(String tableName, List<String> columns,
                                          List<Map<String, Object>> batch, int startIndex,
                                          AtomicInteger successCount, List<InsertError> errors,
                                          Exception batchError) {

        System.err.println("Batch failed, processing individually: " + batchError.getMessage());

        String sql = buildInsertSQL(tableName, columns);

        for (int j = 0; j < batch.size(); j++) {
            int globalIndex = startIndex + j;
            Map<String, Object> row = batch.get(j);

            try {
                int result = jdbcTemplate.update(sql, getRowValues(columns, row));
                if (result > 0) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                errors.add(new InsertError(globalIndex, e.getMessage(), row));
                System.err.printf("Failed to insert row %d: %s%n", globalIndex, e.getMessage());
            }
        }
    }

    /**
     * Multi-value INSERT SQL: INSERT INTO table (col1, col2) VALUES (?, ?), (?, ?), ...
     */
    private String buildMultiValueInsertSQL(String tableName, List<String> columns, int rowCount) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES ");

        // Создаем placeholders для всех строк: (?, ?, ?), (?, ?, ?), ...
        String rowPlaceholder = "(" + "?, ".repeat(columns.size() - 1) + "?)";
        sql.append(String.join(", ", Collections.nCopies(rowCount, rowPlaceholder)));

        return sql.toString();
    }

    /**
     * Классический INSERT SQL для одной строки
     */
    private String buildInsertSQL(String tableName, List<String> columns) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(tableName).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES (");
        sql.append("?, ".repeat(columns.size() - 1));
        sql.append("?)");

        return sql.toString();
    }

    /**
     * "Выравнивает" значения всех строк в один список для multi-value INSERT
     */
    private List<Object> flattenBatchValues(List<String> columns, List<Map<String, Object>> batch) {
        List<Object> allValues = new ArrayList<>(columns.size() * batch.size());

        for (Map<String, Object> row : batch) {
            for (String column : columns) {
                allValues.add(row.get(column));
            }
        }

        return allValues;
    }

    /**
     * Получает значения строки в правильном порядке колонок
     */
    private Object[] getRowValues(List<String> columns, Map<String, Object> row) {
        Object[] values = new Object[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            values[i] = row.get(columns.get(i));
        }
        return values;
    }

    /**
     * Экранирование имен колонок если нужно
     */
    private String escapeColumnName(String columnName) {
        // Если имя содержит спецсимволы или ключевые слова - экранируем
        if (columnName.matches(".*[^a-zA-Z0-9_].*") || isSqlKeyword(columnName)) {
            return "\"" + columnName + "\"";
        }
        return columnName;
    }

    /**
     * Проверка на ключевые слова SQL
     */
    private boolean isSqlKeyword(String word) {
        Set<String> keywords = Set.of("select", "insert", "update", "delete", "from", "where",
                "group", "order", "by", "having", "join", "inner", "outer",
                "left", "right", "full", "cross", "natural", "as", "on",
                "and", "or", "not", "in", "like", "between", "is", "null");
        return keywords.contains(word.toLowerCase());
    }

    /**
     * Проверка поддержки multi-value INSERT
     */
    private boolean isMultiValueInsertSupported() {
        // PostgreSQL, MySQL, SQLite поддерживают multi-value INSERT
        try {
            String dbName = jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName();
            return dbName.toLowerCase().contains("postgresql") ||
                    dbName.toLowerCase().contains("mysql") ||
                    dbName.toLowerCase().contains("sqlite");
        } catch (Exception e) {
            return false; // В случае ошибки используем классический batch
        }
    }

    // ========== BATCH SETTER CLASSES ==========

    /**
     * BatchPreparedStatementSetter для multi-value INSERT
     */
    private static class MultiValueBatchPreparedStatementSetter implements BatchPreparedStatementSetter {
        private final List<String> columns;
        private final List<Map<String, Object>> batch;
        private final int valuesPerRow;

        public MultiValueBatchPreparedStatementSetter(List<String> columns, List<Map<String, Object>> batch) {
            this.columns = columns;
            this.batch = batch;
            this.valuesPerRow = columns.size();
        }

        @Override
        public void setValues(PreparedStatement ps, int batchIndex) throws SQLException {
            // Для multi-value все значения передаются в одном batch
            int totalValues = batch.size() * valuesPerRow;
            for (int i = 0; i < totalValues; i++) {
                int rowIndex = i / valuesPerRow;
                int colIndex = i % valuesPerRow;
                Object value = batch.get(rowIndex).get(columns.get(colIndex));
                ps.setObject(i + 1, value);
            }
        }

        @Override
        public int getBatchSize() {
            return 1; // Для multi-value весь batch - один statement
        }
    }

    /**
     * BatchPreparedStatementSetter для классического batch insert
     */
    private static class SimpleBatchPreparedStatementSetter implements BatchPreparedStatementSetter {
        private final List<String> columns;
        private final List<Map<String, Object>> batch;

        public SimpleBatchPreparedStatementSetter(List<String> columns, List<Map<String, Object>> batch) {
            this.columns = columns;
            this.batch = batch;
        }

        @Override
        public void setValues(PreparedStatement ps, int i) throws SQLException {
            Map<String, Object> row = batch.get(i);
            for (int j = 0; j < columns.size(); j++) {
                ps.setObject(j + 1, row.get(columns.get(j)));
            }
        }

        @Override
        public int getBatchSize() {
            return batch.size();
        }
    }

    // ========== SUPPORT CLASSES ==========

    /**
     * Результат пакетной вставки
     */
    public static class BatchInsertResult {
        private final int successCount;
        private final int errorCount;
        private final List<InsertError> errors;

        public BatchInsertResult(int successCount, int errorCount, List<InsertError> errors) {
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.errors = errors;
        }

        // Getters
        public int getSuccessCount() { return successCount; }
        public int getErrorCount() { return errorCount; }
        public List<InsertError> getErrors() { return errors; }
    }

    /**
     * Информация об ошибке вставки
     */
    public static class InsertError {
        private final int rowIndex;
        private final String message;
        private final Map<String, Object> rowData;

        public InsertError(int rowIndex, String message, Map<String, Object> rowData) {
            this.rowIndex = rowIndex;
            this.message = message;
            this.rowData = rowData;
        }

        // Getters
        public int getRowIndex() { return rowIndex; }
        public String getMessage() { return message; }
        public Map<String, Object> getRowData() { return rowData; }
    }

    // ========== ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ==========

    /**
     * Пакетная вставка с прогрессом (для больших файлов)
     */
    @Transactional
    public BatchInsertResult batchInsertWithProgress(String tableName, List<Map<String, Object>> rows,
                                                     ProgressCallback callback) {
        if (rows == null || rows.isEmpty()) {
            return new BatchInsertResult(0, 0, Collections.emptyList());
        }

        BatchInsertResult result = batchInsert(tableName, rows);

        if (callback != null) {
            callback.onComplete(result);
        }

        return result;
    }

    /**
     * Настройка размера пачки
     */
    public void setBatchSize(int batchSize) {
        this.currentBatchSize = Math.max(100, Math.min(10000, batchSize)); // Ограничения 100-10000
    }

    /**
     * Получение статистики производительности
     */
    public PerformanceStats getPerformanceStats() {
        return new PerformanceStats(currentBatchSize);
    }

    /**
     * Колбэк для отслеживания прогресса
     */
    public interface ProgressCallback {
        void onComplete(BatchInsertResult result);
    }

    /**
     * Статистика производительности
     */
    public static class PerformanceStats {
        private final int currentBatchSize;

        public PerformanceStats(int currentBatchSize) {
            this.currentBatchSize = currentBatchSize;
        }

        public int getCurrentBatchSize() { return currentBatchSize; }
    }
}