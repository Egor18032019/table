package org.tablebuilder.demo.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TableListRepository extends JpaRepository<TableList, Long> {
    List<TableList> findByTableId(Long id);

    TableList findByTableIdAndOriginalListName(Long id, String sheetName);

    void deleteByTableId(Long id);
}
