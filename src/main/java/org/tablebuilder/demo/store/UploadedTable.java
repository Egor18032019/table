package org.tablebuilder.demo.store;


import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Для хранения оригинальных имен загруженных таблиц,
 * то есть имя файла Сотрудники.xlsx => sotrudniki
 */
@Entity
@Table(name = "uploaded_tables")
@Data
public class UploadedTable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 63)
    private String internalName; // sotrudniki

    @Column(nullable = false)
    private String displayName; // Сотрудники.xlsx

    @Column(nullable = false)
    private String username;

    private LocalDateTime createdAt = LocalDateTime.now();
}