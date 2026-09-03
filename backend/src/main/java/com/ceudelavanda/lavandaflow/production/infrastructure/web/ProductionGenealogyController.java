package com.ceudelavanda.lavandaflow.production.infrastructure.web;

import com.ceudelavanda.lavandaflow.production.application.genealogy.GenealogyDirection;
import com.ceudelavanda.lavandaflow.production.application.genealogy.GetBatchGenealogy;
import com.ceudelavanda.lavandaflow.production.infrastructure.web.response.BatchGenealogyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/production/genealogy")
@RequiredArgsConstructor
@Tag(name = "Production genealogy")
public class ProductionGenealogyController {

    private final GetBatchGenealogy getBatchGenealogy;

    @GetMapping("/batches/{batchId}")
    @Operation(
        summary = "Resolve recursive batch genealogy",
        description = "Traverses persisted production relationships upstream, downstream, or in both directions. "
            + "Batch and execution UUIDs are graph identity; lot codes are display data only."
    )
    public BatchGenealogyResponse getBatchGenealogy(
        @PathVariable UUID batchId,
        @Parameter(description = "Recursive traversal direction: UPSTREAM, DOWNSTREAM, or BOTH")
        @RequestParam(defaultValue = "BOTH") GenealogyDirection direction
    ) {
        return BatchGenealogyResponse.from(getBatchGenealogy.execute(batchId, direction));
    }
}
