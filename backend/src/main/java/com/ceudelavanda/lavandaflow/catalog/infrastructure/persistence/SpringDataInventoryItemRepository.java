package com.ceudelavanda.lavandaflow.catalog.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.catalog.domain.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataInventoryItemRepository extends JpaRepository<InventoryItemJpaEntity, UUID> {

    List<InventoryItemJpaEntity> findAllByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from InventoryItemJpaEntity item where item.id = :inventoryItemId")
    Optional<InventoryItemJpaEntity> findByIdForUpdate(
        @Param("inventoryItemId") UUID inventoryItemId
    );

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
