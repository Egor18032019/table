package org.tablebuilder.demo.model;

import lombok.Data;


/**
 * Класс для фильтрации
 *     Поддерживаемые операторы:
 *         contains — текст содержит подстроку (регистронезависимо)
 *         equals — точное совпадение
 *         gt / lt / gte / lte — для чисел и дат
 *         between — диапазон (value → value2)
 */
@Data
public class FilterRequest {
    private String column;      // internalName столбца
    //todo сделать enum
    private String operator;    // "contains", "equals", "gt", "lt", "between"
    private String value;       // значение для фильтра
    private String value2;      // второе значение (для between)
}