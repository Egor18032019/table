package org.tablebuilder.demo.service;

import org.tablebuilder.demo.model.ColumnDefinitionDTO;
import org.tablebuilder.demo.model.ListDTO;
import org.tablebuilder.demo.model.TableTemplateDTO;
import org.tablebuilder.demo.store.UploadedTable;
import org.tablebuilder.demo.utils.ColumnType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tablebuilder.demo.utils.NameUtils;

import java.util.*;
import java.util.stream.Collectors;

import static org.tablebuilder.demo.utils.NameUtils.*;

@Service
public class DynamicTableService {
    @Autowired
    private MetadataService metadataService;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    /**
     * Создание таблицы если ее нет
     */
    @Transactional
    public Map<Object, ColumnType> ensureTableExists(String tableName, List<String> originalColumnNames,
                                                     List<String> columnNames, List<Map<String, Object>> sampleData) {

        // ВАЖНО: Проверяем что имя таблицы не пустое
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }

        String safeTableName = sanitizeName(tableName);

        // ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА после санации
        if (safeTableName.isEmpty()) {
            safeTableName = "table_" + System.currentTimeMillis();
        }

        System.out.println("Final table name: " + safeTableName);

        // Валидация имен колонок
        validateColumnNames(columnNames);

        // Проверяем, существует ли таблица
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = ?)",
                Boolean.class,
                safeTableName
        );

        Map<Object, ColumnType> columnTypes = new HashMap<>();
        if (Boolean.FALSE.equals(exists)) {
            // Анализ типов
            columnTypes = analyzeColumnTypes(columnNames, sampleData);

            System.out.println("=== COLUMN TYPES ANALYSIS ===");
            for (Map.Entry<Object, ColumnType> entry : columnTypes.entrySet()) {
                System.out.println("Column: " + entry.getKey() + " -> " + entry.getValue());
            }

            // Создаём таблицу
            StringBuilder sql = new StringBuilder("CREATE TABLE ");
            sql.append(safeTableName).append(" (id BIGSERIAL PRIMARY KEY");

            for (String colName : columnNames) {
                String safeColName = sanitizeName(colName);
                sql.append(", ").append(safeColName).append(" ");

                ColumnType type = columnTypes.get(colName);
                System.out.println("Creating column: " + safeColName + " as " + type);

                switch (type) {
                    case NUMBER -> sql.append("NUMERIC");
                    case DATE -> sql.append("DATE");
                    case BOOLEAN -> sql.append("BOOLEAN");
                    default -> {
                        // По умолчанию TEXT для безопасности
                        System.out.println("Using TEXT for column: " + safeColName);
                        sql.append("TEXT");
                    }
                }
            }

            sql.append(");");

            System.out.println("Final SQL: " + sql);

            try {
                jdbcTemplate.execute(sql.toString());
                System.out.println("Table created successfully: " + safeTableName);
            } catch (Exception e) {
                System.err.println("Failed to create table: " + e.getMessage());
                throw e;
            }
        } else {
            System.out.println("[INFO] Table already exists: " + safeTableName);
        }

        return columnTypes;
    }

    /**
     * Валидация уникальности имен колонок
     */
    private void validateColumnNames(List<String> columnNames) {
        Set<String> uniqueNames = new HashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (String name : columnNames) {
            if (!uniqueNames.add(name)) {
                duplicates.add(name);
            }
        }

        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Duplicate column names found: " + duplicates +
                            ". Please ensure all column names are unique."
            );
        }
    }

    /**
     * Анализ типов данных для всех колонок
     */
    private Map<Object, ColumnType> analyzeColumnTypes(List<String> columnNames, List<Map<String, Object>> sampleData) {
        Map<Object, ColumnType> columnTypes = new HashMap<>();

        for (String colName : columnNames) {
            List<Object> values = new ArrayList<>();
            for (Map<String, Object> row : sampleData) {
                Object value = row.get(colName);
                values.add(value);
            }

            ColumnType detectedType = detectColumnType(values);
            System.out.println("Column '" + colName + "' detected as: " + detectedType);

            columnTypes.put(colName, detectedType);
        }

        return columnTypes;
    }

    /**
     * Упрощенное и надежное определение типов
     */
    private ColumnType detectColumnType(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return ColumnType.TEXT; // По умолчанию TEXT
        }

        // Считаем непустые значения
        List<Object> nonEmptyValues = values.stream()
                .filter(value -> value != null && !value.toString().trim().isEmpty())
                .collect(Collectors.toList());

        if (nonEmptyValues.isEmpty()) {
            return ColumnType.TEXT; // Все значения пустые
        }

        // Проверяем типы только для непустых значений
        boolean allNumbers = true;
        boolean allBooleans = true;
        boolean allDates = true;

        for (Object value : nonEmptyValues) {
            if (value == null) continue;

            // Проверка на Boolean (только строгие значения)
            if (!isStrictBoolean(value)) {
                allBooleans = false;
            }

            // Проверка на Number
            if (!isNumeric(value)) {
                allNumbers = false;
            }

            // Проверка на Date
            if (!isDate(value)) {
                allDates = false;
            }
        }

        // Приоритет типов: Boolean -> Date -> Number -> Text
        if (allBooleans && nonEmptyValues.size() >= 2) { // Нужно хотя бы 2 boolean значения
            return ColumnType.BOOLEAN;
        }
        if (allDates) {
            return ColumnType.DATE;
        }
        if (allNumbers) {
            return ColumnType.NUMBER;
        }

        // По умолчанию TEXT
        return ColumnType.TEXT;
    }

    /**
     * Строгая проверка на Boolean
     */
    private boolean isStrictBoolean(Object value) {
        if (value == null) return false;

        String str = value.toString().trim().toLowerCase();

        // Только явные boolean значения
        return "true".equals(str) || "false".equals(str) ||
                "1".equals(str) || "0".equals(str);
    }

    /**
     * Проверка на число
     */
    private boolean isNumeric(Object value) {
        if (value == null) return false;

        // Если это уже число
        if (value instanceof Number) {
            return true;
        }

        String str = value.toString().trim();
        if (str.isEmpty()) return false;

        try {
            Double.parseDouble(str.replace(",", "."));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Проверка на дату
     */
    private boolean isDate(Object value) {
        if (value == null) return false;

        // Если это уже дата
        if (value instanceof java.util.Date) {
            return true;
        }

        String str = value.toString().trim();
        if (str.isEmpty()) return false;

        // Простые проверки форматов дат
        return str.matches("\\d{4}-\\d{2}-\\d{2}") ||  // yyyy-mm-dd
                str.matches("\\d{2}\\.\\d{2}\\.\\d{4}") || // dd.mm.yyyy
                str.matches("\\d{2}/\\d{2}/\\d{4}");   // dd/mm/yyyy
    }

    /**
     * Вставка строки
     */
    public void insertRow(String tableName, Map<String, Object> rowData) {
        if (rowData.isEmpty()) {
            return;
        }

        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : rowData.entrySet()) {
            if (columns.length() > 0) {
                columns.append(", ");
                placeholders.append(", ");
            }
            columns.append(entry.getKey());
            placeholders.append("?");
            values.add(entry.getValue());
        }

        String sql = "INSERT INTO " + tableName + " (" + columns + ") VALUES (" + placeholders + ")";
        jdbcTemplate.update(sql, values.toArray());
    }

    /**
     * Получение всех строк таблицы
     */
    public List<Map<String, Object>> getAllRows(String tableName) {
        return jdbcTemplate.queryForList("SELECT * FROM " + tableName);
    }

    public void ensureTableExists(TableTemplateDTO template) {
        String baseFileName = template.getTableName();
        String originalFilename = baseFileName + ".xlsx";
        String internalTableName = NameUtils.toValidSqlName(baseFileName);

        if (internalTableName.isEmpty()) {
            internalTableName = "table_" + System.currentTimeMillis();
        }

        // Сохраняем метаданные таблицы
        UploadedTable savedTable = metadataService.saveUploadedTable(originalFilename, internalTableName, "frontend");

        // Обрабатываем каждый лист из template
        for (ListDTO listDTO : template.getColumns()) {
            String originalSheetName = listDTO.getNameList();
            String listName = NameUtils.toValidSqlName(originalSheetName);
            String tableName = sanitizeTableName(internalTableName + "__" + listName);

            if (tableName.isEmpty()) {
                tableName = "table_" + System.currentTimeMillis() + "_" + originalSheetName;
            }

            System.out.println("Creating table: " + tableName);

            // Сохраняем метаданные листа
            metadataService.saveTableList(savedTable, tableName, originalSheetName);

            // Проверяем, существует ли таблица
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = ?)",
                    Boolean.class,
                    tableName
            );

            if (Boolean.FALSE.equals(exists)) {
                createTableFromListDTO(tableName, listDTO);
                // Сохраняем метаданные колонок
                saveColumnsMetadata(savedTable, listDTO, tableName);
            } else {
                System.out.println("[INFO] Table already exists: " + tableName);
            }
        }
    }

    /**
     * Создание таблицы для конкретного листа
     */
    private void createTableFromListDTO(String tableName, ListDTO listDTO) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ");
        sql.append(tableName).append(" (id BIGSERIAL PRIMARY KEY");

        // Валидация имен колонок
        validateColumnNamesColumnDefinitionDTO(listDTO.getColumns());

        for (ColumnDefinitionDTO col : listDTO.getColumns()) {
            String safeColName = sanitizeColumnName(col.getName(), listDTO.getColumns().indexOf(col));
            sql.append(", ").append(safeColName).append(" ");

            ColumnType type = detectColumnType(col.getType());
            System.out.println("Creating column: " + safeColName + " as " + type);

            switch (type) {
                case NUMBER -> sql.append("NUMERIC");
                case DATE -> sql.append("DATE");
                case BOOLEAN -> sql.append("BOOLEAN");
                default -> {
                    System.out.println("Using TEXT for column: " + safeColName);
                    sql.append("TEXT");
                }
            }

            // Добавляем ограничения если указаны
            if (col.isRequired()) {
                sql.append(" NOT NULL");
            }
            if (col.isUnique()) {
                sql.append(" UNIQUE");
            }
        }

        sql.append(");");

        System.out.println("Final SQL: " + sql);

        try {
            jdbcTemplate.execute(sql.toString());
            System.out.println("Table created successfully: " + tableName);
        } catch (Exception e) {
            System.err.println("Failed to create table: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Сохранение метаданных колонок
     */
    private void saveColumnsMetadata(UploadedTable savedTable, ListDTO listDTO, String tableName) {
        List<String> originalColumnNames = new ArrayList<>();
        List<String> internalColumnNames = new ArrayList<>();

        for (ColumnDefinitionDTO col : listDTO.getColumns()) {
            originalColumnNames.add(col.getName());
            internalColumnNames.add(sanitizeColumnName(col.getName(), listDTO.getColumns().indexOf(col)));
        }

        metadataService.saveTableMetadata(
                savedTable,
                originalColumnNames,
                internalColumnNames,
                tableName
        );
    }

    /**
     * Валидация имен колонок
     */
    private void validateColumnNamesColumnDefinitionDTO(List<ColumnDefinitionDTO> columns) {
        java.util.Set<String> uniqueNames = new java.util.HashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (ColumnDefinitionDTO col : columns) {
            String colName = sanitizeName(col.getName());
            if (!uniqueNames.add(colName)) {
                duplicates.add(col.getName());
            }
        }

        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(
                    "Duplicate column names found: " + duplicates +
                            ". Please ensure all column names are unique."
            );
        }
    }

    /**
     * Определение типа колонки из строки
     */
    public ColumnType detectColumnType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return ColumnType.TEXT;
        }

        switch (type.toUpperCase()) {
            case "STRING":
                return ColumnType.TEXT;
            case "NUMBER":
            case "NUMERIC":
            case "INTEGER":
            case "FLOAT":
            case "DOUBLE":
                return ColumnType.NUMBER;
            case "DATE":
            case "DATETIME":
            case "TIMESTAMP":
                return ColumnType.DATE;
            case "BOOLEAN":
            case "BOOL":
                return ColumnType.BOOLEAN;
            default:
                return ColumnType.TEXT;
        }
    }
}

/*
        switch (col.getType().toUpperCase()) {
                case "STRING" -> sql.append("TEXT");
                case "NUMBER" -> sql.append("NUMERIC");
                case "DATE" -> sql.append("DATE");
                case "BOOLEAN" -> sql.append("BOOLEAN");
                case "ENUM" -> sql.append("TEXT");
                default -> sql.append("TEXT");
            }
 */