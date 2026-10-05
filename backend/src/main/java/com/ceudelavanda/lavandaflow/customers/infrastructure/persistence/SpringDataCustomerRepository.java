package com.ceudelavanda.lavandaflow.customers.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

interface SpringDataCustomerRepository extends JpaRepository<CustomerJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerJpaEntity c where c.id = :id")
    Optional<CustomerJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        select c from CustomerJpaEntity c
        where (:text is null or lower(c.name) like :text escape '!'
            or lower(c.email) like :text escape '!'
            or (:phone is not null and c.phone like :phone escape '!'))
          and (:active is null or c.active = :active)
        order by c.name, c.id
        """)
    Page<CustomerJpaEntity> search(@Param("text") String text, @Param("phone") String phone,
                                 @Param("active") Boolean active, Pageable pageable);
}
