package com.ceudelavanda.lavandaflow.suppliers.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface SpringDataSupplierRepository extends JpaRepository<SupplierJpaEntity, UUID> {

    @Query(
        value = """
            select supplier
            from SupplierJpaEntity supplier
            where (:namePattern is null or lower(supplier.name) like :namePattern escape '!')
              and (:active is null or supplier.active = :active)
            order by lower(supplier.name), supplier.id
            """,
        countQuery = """
            select count(supplier)
            from SupplierJpaEntity supplier
            where (:namePattern is null or lower(supplier.name) like :namePattern escape '!')
              and (:active is null or supplier.active = :active)
            """
    )
    Page<SupplierJpaEntity> search(
        @Param("namePattern") String namePattern,
        @Param("active") Boolean active,
        Pageable pageable
    );
}
