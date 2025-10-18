package org.tablebuilder.demo.service;

import jakarta.transaction.Transactional;
import org.tablebuilder.demo.model.ExcelImportResult;
import org.tablebuilder.demo.store.*;
import org.tablebuilder.demo.utils.ColumnType;
import org.tablebuilder.demo.utils.NameUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExcelImportService {

    @Autowired
    private DynamicTableService dynamicTableService;
    @Autowired
    private UploadedTableRepository uploadedTableRepository;
    @Autowired
    private MetadataService metadataService;
    @Autowired
    private TableListRepository tableListRepository;
    @Autowired
    private TableColumnRepository tableColumnRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private BatchInsertService batchInsertService;

    @Transactional
    public ExcelImportResult importExcel(MultipartFile file, String username) {
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                return new ExcelImportResult(false, 0, "", "Filename is null");
            }

            // Убираем расширение
            String baseFileName = originalFilename.replaceAll("\\.[^.]*$", "");
            if (baseFileName.trim().isEmpty()) {
                baseFileName = "unknown_file_" + System.currentTimeMillis();
            }

            String internalTableName = NameUtils.toValidSqlName(baseFileName);
            if (internalTableName.isEmpty()) {
                internalTableName = "table_" + System.currentTimeMillis();
            }

            // Проверяем, существует ли уже такой файл
            UploadedTable existingTable = uploadedTableRepository.findByDisplayName(originalFilename);
            UploadedTable savedTable;

            if (existingTable != null) {
                System.out.println("File already exists, deleting old data: " + originalFilename);
                // Удаляем старые данные
                deleteExistingTableData(existingTable);
                // Обновляем метаданные
                existingTable.setInternalName(internalTableName);
                existingTable.setUsername(username);
                savedTable = uploadedTableRepository.save(existingTable);
            } else {
                // Создаем новую таблицу
                savedTable = metadataService.saveUploadedTable(originalFilename, internalTableName, username);
            }

            try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
                int totalRowsImported = 0;
                List<String> processedTables = new ArrayList<>();

                // Проходим по всем листам
                for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                    Sheet sheet = workbook.getSheetAt(sheetIndex);
                    String sheetName = workbook.getSheetName(sheetIndex);

                    // Пропускаем пустые листы (минимум заголовок + 1 строка данных)
                    if (sheet.getPhysicalNumberOfRows() < 2) {
                        System.out.println("Skipping empty sheet: " + sheetName);
                        continue;
                    }

                    // === Генерируем безопасное имя таблицы ===
                    String safeSheetName = NameUtils.toValidSqlName(sheetName);
                    if (safeSheetName.isEmpty()) {
                        safeSheetName = "sheet_" + sheetIndex;
                    }

                    String tableName = NameUtils.toValidSqlName(internalTableName + "__" + safeSheetName);
                    if (tableName.isEmpty()) {
                        tableName = "table_" + System.currentTimeMillis() + "_" + sheetIndex;
                    }

                    System.out.println("Creating table: " + tableName);

                    // Удаляем старый лист если существует
                    TableList existingList = tableListRepository.findByTableIdAndOriginalListName(savedTable.getId(), sheetName);
                    if (existingList != null) {
                        System.out.println("Deleting old sheet data: " + sheetName);
                        // Удаляем старую таблицу из БД
                        dropTableIfExists(existingList.getListName());
                        // Удаляем метаданные колонок
                        tableColumnRepository.deleteByTableIdAndListName(savedTable.getId(), existingList.getListName());
                        // Удаляем метаданные листа
                        tableListRepository.delete(existingList);
                    }

                    // Сохраняем метаданные листа
                    metadataService.saveTableList(savedTable, tableName, sheetName);

                    // Определяем количество столбцов по первой строке (заголовкам)
                    Row firstRow = sheet.getRow(0);
                    if (firstRow == null) {
                        continue;
                    }

                    int maxColumns = firstRow.getLastCellNum();
                    if (maxColumns <= 0) {
                        continue;
                    }

                    // Парсим заголовки с гарантией уникальности
                    List<String> originalColumnNames = new ArrayList<>();
                    List<String> columnNames = new ArrayList<>();
                    Set<String> usedNames = new HashSet<>();

                    for (int i = 0; i < maxColumns; i++) {
                        Cell cell = firstRow.getCell(i);
                        String originalName = (cell == null || cell.toString().trim().isEmpty())
                                ? "Column_" + (i + 1)
                                : cell.toString().trim();

                        // Создаем уникальное SQL-имя
                        String sqlName = NameUtils.generateUniqueColumnName(originalName, i, usedNames);

                        originalColumnNames.add(originalName);
                        columnNames.add(sqlName);
                        usedNames.add(sqlName);
                    }

                    System.out.println("Columns found: " + columnNames.size());

                    // === СБОР SAMPLE DATA ===
                    List<Map<String, Object>> sampleData = new ArrayList<>();
                    int sampleSize = Math.min(20, sheet.getLastRowNum());

                    // Собираем данные НАЧИНАЯ С ПЕРВОЙ СТРОКИ ДАННЫХ (не заголовка)
                    for (int r = 1; r <= Math.min(sampleSize + 1, sheet.getLastRowNum()); r++) {
                        Row row = sheet.getRow(r);
                        if (row == null || isEmptyRow(row)) {
                            continue;
                        }

                        Map<String, Object> rowData = new HashMap<>();
                        boolean hasData = false;

                        for (int c = 0; c < columnNames.size(); c++) {
                            Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                            Object cellValue = getCellValue(cell);

                            if (cellValue != null && !cellValue.toString().trim().isEmpty()) {
                                hasData = true;
                            }
                            rowData.put(columnNames.get(c), cellValue);
                        }

                        if (hasData) {
                            sampleData.add(rowData);
                        }
                    }

                    // Если нет данных для анализа, используем TEXT по умолчанию
                    if (sampleData.isEmpty()) {
                        System.out.println("No sample data found, using TEXT for all columns");
                        for (int r = 1; r <= Math.min(5, sheet.getLastRowNum()); r++) {
                            Row row = sheet.getRow(r);
                            if (row == null) continue;

                            Map<String, Object> rowData = new HashMap<>();
                            for (int c = 0; c < columnNames.size(); c++) {
                                Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                                rowData.put(columnNames.get(c), getCellValue(cell));
                            }
                            sampleData.add(rowData);
                        }
                    }

                    System.out.println("Sample data collected: " + sampleData.size() + " rows");

                    // Создаём таблицу
                    Map<Object, ColumnType> columnTypes = dynamicTableService.ensureTableExists(
                            tableName, originalColumnNames, columnNames, sampleData);

                    // Вставляем данные (начиная со второй строки - данные)
                    int rowsImported = 0;
                    List<Map<String, Object>> allRows = new ArrayList<>();

                    for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                        Row row = sheet.getRow(i);
                        if (row == null || isEmptyRow(row)) continue;

                        Map<String, Object> rowData = new HashMap<>();
                        for (int j = 0; j < columnNames.size(); j++) {
                            Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                            Object value = getCellValue(cell);
                            rowData.put(columnNames.get(j), value);
                        }
                        allRows.add(rowData);
                        rowsImported++;
                    }

                    // Вставляем все данные одним пакетом
                    if (!allRows.isEmpty()) {
                        BatchInsertService.BatchInsertResult result = batchInsertService.batchInsert(tableName, allRows);
                        System.out.println("Batch insert result: " + result.getSuccessCount() + " success, " + result.getErrorCount() + " errors");
                    }

                    totalRowsImported += rowsImported;
                    processedTables.add(tableName);

                    // Сохраняем метаданные для этого листа
                    metadataService.saveTableMetadata(
                            savedTable,
                            originalColumnNames,
                            columnNames,
                            tableName
                    );

                    System.out.println("Sheet processed: " + sheetName + ", rows: " + rowsImported);
                }

                if (processedTables.isEmpty()) {
                    return new ExcelImportResult(false, 0, originalFilename, "No valid sheets found");
                }

                String message = existingTable != null ?
                        "File re-imported successfully. Tables: " + processedTables :
                        "Import successful. Tables: " + processedTables;

                return new ExcelImportResult(
                        true,
                        totalRowsImported,
                        String.join(", ", processedTables),
                        message
                );
            }

        } catch (IOException e) {
            return new ExcelImportResult(false, 0, "", "File reading error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return new ExcelImportResult(false, 0, "", "Error: " + e.getMessage());
        }
    }

    /**
     * Удаление существующих данных таблицы
     */
    private void deleteExistingTableData(UploadedTable table) {
        try {
            // Получаем все листы таблицы
            List<TableList> tableLists = tableListRepository.findByTableId(table.getId());

            for (TableList tableList : tableLists) {
                // Удаляем таблицу из БД
                dropTableIfExists(tableList.getListName());
            }

            // Удаляем метаданные колонок
            tableColumnRepository.deleteByTableId(table.getId());

            // Удаляем метаданные листов
            tableListRepository.deleteByTableId(table.getId());

            System.out.println("Deleted old data for table: " + table.getDisplayName());

        } catch (Exception e) {
            System.err.println("Error deleting old table data: " + e.getMessage());
        }
    }

    /**
     * Удаление таблицы из БД если существует
     */
    private void dropTableIfExists(String tableName) {
        try {
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + tableName);
            System.out.println("Dropped table: " + tableName);
        } catch (Exception e) {
            System.err.println("Error dropping table " + tableName + ": " + e.getMessage());
        }
    }

    /**
     * Проверяет, является ли строка полностью пустой
     */
    private boolean isEmptyRow(Row row) {
        if (row == null) return true;

        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                Object value = getCellValue(cell);
                if (value != null && !value.toString().trim().isEmpty()) {
                    return false; // Нашли непустую ячейку
                }
            }
        }
        return true; // Все ячейки пустые
    }

    /**
     * Улучшенный метод получения значения ячейки
     */
    private Object getCellValue(Cell cell) {
        if (cell == null) return null;

        try {
            switch (cell.getCellType()) {
                case STRING:
                    String stringValue = cell.getStringCellValue().trim();
                    return stringValue.isEmpty() ? null : stringValue;

                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue();
                    } else {
                        double numericValue = cell.getNumericCellValue();
                        // Для целых чисел возвращаем Long, для дробных - Double
                        if (numericValue == Math.floor(numericValue) && !Double.isInfinite(numericValue)) {
                            return (long) numericValue;
                        }
                        return numericValue;
                    }

                case BOOLEAN:
                    return cell.getBooleanCellValue();

                case FORMULA:
                    try {
                        return getFormulaCellValue(cell);
                    } catch (Exception e) {
                        return null;
                    }

                case BLANK:
                    return null;

                default:
                    return null;
            }
        } catch (Exception e) {
            System.err.println("Error reading cell value at " + cell.getAddress() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Получение значения формульной ячейки
     */
    private Object getFormulaCellValue(Cell cell) {
        try {
            switch (cell.getCachedFormulaResultType()) {
                case STRING:
                    return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue();
                    }
                    return cell.getNumericCellValue();
                case BOOLEAN:
                    return cell.getBooleanCellValue();
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Вставка строки с конвертацией типов
     */
    private void insertRow(String tableName, List<String> columnNames, Map<Object, ColumnType> columnTypes, Map<String, Object> rowData) {
        StringBuilder columns = new StringBuilder("INSERT INTO " + tableName + " (");
        StringBuilder placeholders = new StringBuilder(" VALUES (");
        List<Object> convertedValues = new ArrayList<>();

        for (String colName : columnNames) {
            ColumnType colType = columnTypes.get(colName);
            Object rawValue = rowData.get(colName);

            columns.append(colName).append(", ");
            placeholders.append("?, ");

            // Конвертируем значение в правильный тип
            Object convertedValue = convertValue(rawValue, colType);
            convertedValues.add(convertedValue);
        }

        String cols = columns.substring(0, columns.length() - 2) + ")";
        String ph = placeholders.substring(0, placeholders.length() - 2) + ")";
        String sql = cols + ph;

        try {
            jdbcTemplate.update(sql, convertedValues.toArray());
        } catch (Exception e) {
            System.err.println("Insert failed for table " + tableName + ": " + e.getMessage());
            System.err.println("SQL: " + sql);
            System.err.println("Values: " + convertedValues);
            throw e;
        }
    }

    /**
     * Конвертация значений при вставке
     */
    private Object convertValue(Object rawValue, ColumnType columnType) {
        // Пустые значения → null
        if (rawValue == null || (rawValue instanceof String && ((String) rawValue).trim().isEmpty())) {
            return null;
        }

        try {
            switch (columnType) {
                case NUMBER:
                    if (rawValue instanceof Number) {
                        return rawValue;
                    }
                    String numericStr = rawValue.toString().trim().replace(",", ".");
                    return new java.math.BigDecimal(numericStr);

                case DATE:
                    if (rawValue instanceof java.util.Date) {
                        return new java.sql.Date(((java.util.Date) rawValue).getTime());
                    }
                    // Конвертация из строки
                    String dateStr = rawValue.toString().trim();
                    return convertToSqlDate(dateStr);

                case BOOLEAN:
                    if (rawValue instanceof Boolean) {
                        return rawValue;
                    }
                    String boolStr = rawValue.toString().trim().toLowerCase();
                    return "true".equals(boolStr) || "1".equals(boolStr) || "да".equals(boolStr) || "yes".equals(boolStr);

                default: // TEXT
                    return rawValue.toString();
            }
        } catch (Exception e) {
            System.err.println("Conversion error for value '" + rawValue + "' to type " + columnType + ": " + e.getMessage());
            // При ошибке конвертации сохраняем как текст
            return rawValue.toString();
        }
    }

    /**
     * Конвертация строки в SQL Date
     */
    private java.sql.Date convertToSqlDate(String dateStr) {
        String[] datePatterns = {
                "yyyy-MM-dd", "dd.MM.yyyy", "dd/MM/yyyy", "MM/dd/yyyy",
                "yyyy.MM.dd", "dd-MM-yyyy", "MM-dd-yyyy"
        };

        for (String pattern : datePatterns) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern);
                sdf.setLenient(false);
                java.util.Date date = sdf.parse(dateStr);
                return new java.sql.Date(date.getTime());
            } catch (Exception e) {
                // пробуем следующий формат
            }
        }

        // Если не удалось распарсить, возвращаем null
        return null;
    }
}