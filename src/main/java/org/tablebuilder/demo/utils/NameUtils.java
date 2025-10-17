package org.tablebuilder.demo.utils;


import java.util.Set;

/**
 * Утилитарный класс для преобразования произвольных строк (включая кириллицу)
 * в валидные SQL-идентификаторы (только [a-z][a-z0-9_]*)
 */
public class NameUtils {

    public static String sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }

        // Заменяем проблемные символы
        String sanitized = name.trim()
                .replaceAll("[^a-zA-Z0-9_]", "_") // заменяем не-ASCII на _
                .replaceAll("_{2,}", "_")         // убираем повторяющиеся _
                .replaceAll("^_|_$", "");         // убираем _ в начале/конце

        // Если после санации имя пустое
        if (sanitized.isEmpty()) {
            return "";
        }

        // Гарантируем, что имя начинается с буквы
        if (!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "col_" + sanitized;
        }

        // Ограничиваем длину (PostgreSQL limit ~63 bytes)
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        return sanitized.toLowerCase();
    }

    public static String toValidSqlName(String name) {
        return sanitizeName(name);
    }

    public static String generateUniqueColumnName(String baseName, int index, Set<String> usedNames) {
        String name = toValidSqlName(baseName);

        // Если имя пустое после санации, создаем базовое имя
        if (name.isEmpty()) {
            name = "column_" + (index + 1);
        }

        // Добавляем суффикс если имя уже используется
        String finalName = name;
        int suffix = 1;
        while (usedNames.contains(finalName)) {
            finalName = name + "_" + suffix;
            suffix++;

            // Защита от бесконечного цикла
            if (suffix > 1000) {
                throw new IllegalStateException("Cannot generate unique column name for: " + baseName);
            }
        }

        return finalName;
    }
}