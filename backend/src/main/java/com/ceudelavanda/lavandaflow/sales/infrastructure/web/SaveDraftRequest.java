package com.ceudelavanda.lavandaflow.sales.infrastructure.web;

import com.ceudelavanda.lavandaflow.sales.application.SaveDraftCommand;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Complete draft fields. Customer is optional. Totals are calculated by the backend; no stock is reserved.")
public record SaveDraftRequest(
    @Schema(nullable = true, description = "Omitted or null leaves the draft without a customer; a supplied customer must be active") UUID customerId,
    @ArraySchema(minItems = 1, arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED)) List<Line> lines
) {
    @Schema(name = "DraftOrderLineRequest", description = "One existing sellable item; decimals may be sent as exact strings. Null ID creates or retains the item line.")
    public record Line(
        @Schema(nullable = true, description = "If supplied, must belong to this order and the same item") UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Active FINISHED_PRODUCT identity using UNIT or MILLILITER") UUID itemId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[0-9]{1,13}(\\.[0-9]{1,6})?$", example = "1.250000", description = "Positive exact quantity in the catalog unit, with no conversion") BigDecimal quantity,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", pattern = "^[0-9]{1,15}(\\.[0-9]{1,4})?$", example = "12.3456", description = "Nonnegative operator-entered BRL price, at most four fractional digits") BigDecimal unitPrice
    ) {}
    SaveDraftCommand toCommand() {
        return new SaveDraftCommand(customerId, lines == null ? null : lines.stream().map(line -> line == null ? null :
            new SaveDraftCommand.Line(line.id(), line.itemId(), line.quantity(), line.unitPrice())).toList());
    }
}
