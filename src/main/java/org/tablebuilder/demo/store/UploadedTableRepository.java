package org.tablebuilder.demo.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UploadedTableRepository extends JpaRepository<UploadedTable, Long> {
    Optional<UploadedTable> findByInternalName(String internalName);
    boolean existsByInternalName(String internalName);

    List<UploadedTable> findByDisplayNameStartingWith(String prefix);

    UploadedTable findByDisplayName(String displayName);
}