package com.ceudelavanda.lavandaflow.sales.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.sales.domain.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
class JpaSaleStockCorrectionRepository implements SaleStockCorrectionRepository {
    private final EntityManager entities;
    public Optional<SaleStockCorrection> find(UUID saleId, UUID id) {
        return Optional.ofNullable(entities.find(SaleStockCorrectionJpaEntity.class,
            new SaleStockCorrectionJpaEntity.Key(saleId, id))).map(SaleStockCorrectionJpaEntity::toDomain);
    }
    public List<SaleStockCorrection> findBySaleId(UUID saleId) {
        return entities.createQuery("select c from SaleStockCorrectionJpaEntity c where c.key.saleId = :saleId order by c.occurredAt, c.key.id", SaleStockCorrectionJpaEntity.class)
            .setParameter("saleId", saleId).getResultList().stream().map(SaleStockCorrectionJpaEntity::toDomain).toList();
    }
    public void insert(SaleStockCorrection correction) {
        entities.persist(new SaleStockCorrectionJpaEntity(correction));
        entities.flush();
    }
}
