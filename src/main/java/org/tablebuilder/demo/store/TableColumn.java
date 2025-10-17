package org.tablebuilder.demo.store;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Для хранения информации о столбцах таблицы,
 * то есть в Excel столбец = "№ мест" а в БД столбец = no_mest
 */
@Entity
@Table(name = "table_columns")
@Data
public class TableColumn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "table_id", nullable = false)
    private UploadedTable table;

    @Column(nullable = false, length = 63)
    private String internalName;

    @Column(nullable = false)
    private String displayName;

    private int originalIndex;

    @Column(nullable = false, length = 63)
    private String listName;
}