package org.tablebuilder.demo.store;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Для хранения информации о листах в файле
 */
@Entity
@Table(name = "table_list")
@Data
public class TableList {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "table_id", nullable = false)
    private UploadedTable table;
    @Column(nullable = false, length = 63)
    private String listName;
    @Column(nullable = false, length = 63)
    private String originalListName;
}
