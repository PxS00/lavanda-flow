package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface SpringDataInventoryItemRepository extends JpaRepository<InventoryItemJpaEntity, UUID> {

    @Query(
        value = """
            select item
            from InventoryItemJpaEntity item
            where (:namePattern is null or lower(item.name) like :namePattern escape '!')
              and (:category is null or item.category = :category)
              and (:active is null or item.active = :active)
            order by lower(item.name), item.id
            """,
        countQuery = """
            select count(item)
            from InventoryItemJpaEntity item
            where (:namePattern is null or lower(item.name) like :namePattern escape '!')
              and (:category is null or item.category = :category)
              and (:active is null or item.active = :active)
            """
    )
    Page<InventoryItemJpaEntity> search(
        @Param("namePattern") String namePattern,
        @Param("category") Category category,
        @Param("active") Boolean active,
        Pageable pageable
    );
}
