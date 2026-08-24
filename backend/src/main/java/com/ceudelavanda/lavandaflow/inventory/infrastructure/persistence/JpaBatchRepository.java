package com.ceudelavanda.lavandaflow.inventory.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.inventory.domain.Batch;
import com.ceudelavanda.lavandaflow.inventory.domain.BatchRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class JpaBatchRepository implements BatchRepository {

    private final SpringDataBatchRepository repository;

    JpaBatchRepository(SpringDataBatchRepository repository) {
        this.repository = repository;
    }

    @Override
    public Batch save(Batch batch) {
        var entity = BatchMapper.toEntity(batch);
        var savedEntity = repository.save(entity);

        return BatchMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Batch> findById(UUID id) {
        return repository.findById(id)
            .map(BatchMapper::toDomain);
    }
}
