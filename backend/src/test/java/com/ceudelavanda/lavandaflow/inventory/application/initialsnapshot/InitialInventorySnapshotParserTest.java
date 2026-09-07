package com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitialInventorySnapshotParserTest {

    private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 9, 5);

    @TempDir
    Path directory;

    private final InitialInventorySnapshotParser parser = new InitialInventorySnapshotParser();

    @Test
    void shouldNormalizeApprovedSourceShapeAndDisambiguateDuplicateReferences() throws IOException {
        var plan = parse("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired
              Scandall, M,10.5,09/2 7
            Scandall, F,0,
              Unique Essence  ,F / C,1.000001,10/27
            """.replace("\n", "\r\n"));

        assertThat(plan.report().totalRowCount()).isEqualTo(3);
        assertThat(plan.report().openingStockCount()).isEqualTo(2);
        assertThat(plan.report().catalogOnlyCount()).isEqualTo(1);
        assertThat(plan.report().rejectedCount()).isZero();
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::sourceRowNumber)
            .containsExactly(1, 2, 3);
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::catalogName)
            .containsExactly("Scandall (M)", "Scandall (F)", "Unique Essence");
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::legacyReference)
            .containsExactly("M", "F", "F/C");
        assertThat(plan.report().rows().get(0).quantity()).isEqualByComparingTo("10.500000");
        assertThat(plan.report().rows().get(0).expiration()).isEqualTo(LocalDate.of(2027, 9, 30));
        assertThat(plan.report().rows().get(1).expiration()).isNull();
    }

    @Test
    void shouldAdjustFinalSnapshotQuantitiesUsingWithdrawalColumn() throws IOException {
        var plan = parse("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada
            Adjusted,F,80,09/27,-50ml
            Spaced,M,80,10/27,- 50ml
            Decimal,C,10.5,11/27,-0.500001ml
            Blank,F/C,1,12/27,
            Catalog only,M/C,50,,-50ml
            """);

        assertThat(plan.report().openingStockCount()).isEqualTo(4);
        assertThat(plan.report().catalogOnlyCount()).isEqualTo(1);
        assertThat(plan.report().rejectedCount()).isZero();
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::quantity)
            .containsExactly(
                new BigDecimal("30.000000"),
                new BigDecimal("30.000000"),
                new BigDecimal("9.999999"),
                new BigDecimal("1.000000"),
                new BigDecimal("0.000000")
            );
        assertThat(plan.report().rows().get(4).outcome()).isEqualTo(InitialInventoryImportOutcome.CATALOG_ONLY);
    }

    @Test
    void shouldRejectInvalidWithdrawalAdjustmentsAndNegativeAdjustedQuantity() throws IOException {
        var plan = parse("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada
            Positive,F,80,09/27,50ml
            Malformed,M,80,09/27,-50liters
            Too precise,C,80,09/27,-0.0000001ml
            Below zero,F/C,10,09/27,-11ml
            Positive blank,M/C,10,,
            """);

        assertThat(plan.report().rejectedCount()).isEqualTo(5);
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsExactly(
                InitialInventoryImportValidationCode.INVALID_WITHDRAWAL_ADJUSTMENT,
                InitialInventoryImportValidationCode.INVALID_WITHDRAWAL_ADJUSTMENT,
                InitialInventoryImportValidationCode.INVALID_WITHDRAWAL_ADJUSTMENT,
                InitialInventoryImportValidationCode.NEGATIVE_ADJUSTED_QUANTITY,
                InitialInventoryImportValidationCode.EXPIRATION_REQUIRED
            );
    }

    @Test
    void shouldAccumulateStableValidationResultsInSourceOrder() throws IOException {
        var plan = parse("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired
            Valid,F,1,09/27
            Too precise,M,1.0000001,09/27
            Zero blank,C,0,
            Positive blank,M/C,2,
            Already expired,F,2,08/26
            Bad columns,F,2
            Bad expiration,F,2,13/27
            Exponent,F,1e2,09/27
            Negative zero,F,-0,09/27
            """);

        assertThat(plan.report().totalRowCount()).isEqualTo(9);
        assertThat(plan.report().openingStockCount()).isEqualTo(1);
        assertThat(plan.report().catalogOnlyCount()).isEqualTo(1);
        assertThat(plan.report().rejectedCount()).isEqualTo(7);
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsExactly(
                null,
                InitialInventoryImportValidationCode.INVALID_QUANTITY,
                null,
                InitialInventoryImportValidationCode.EXPIRATION_REQUIRED,
                InitialInventoryImportValidationCode.EXPIRATION_BEFORE_EFFECTIVE_DATE,
                InitialInventoryImportValidationCode.MALFORMED_ROW,
                InitialInventoryImportValidationCode.INVALID_EXPIRATION,
                InitialInventoryImportValidationCode.INVALID_QUANTITY,
                InitialInventoryImportValidationCode.INVALID_QUANTITY
            );
    }

    @Test
    void shouldRejectUnresolvableDuplicatesAndUnexpectedHeader() throws IOException {
        var duplicatePlan = parse("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired
            Same,F,1,09/27
            same,F,2,10/27
            """);

        assertThat(duplicatePlan.report().rows())
            .allMatch(row -> row.outcome() == InitialInventoryImportOutcome.REJECTED)
            .allMatch(row -> row.validationCode() == InitialInventoryImportValidationCode.DUPLICATE_NAME);

        var wrongHeader = write("Name,Genero,Ml Disponiveis,Expired\nItem,F,1,09/27\n");
        assertThatThrownBy(() -> parser.parse(
            wrongHeader, InitialInventoryImportMode.DRY_RUN, EFFECTIVE_DATE
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("CSV header must be exactly");

        var extraColumn = write("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada,Unexpected
            Item,F,1,09/27,,
            """);
        assertThatThrownBy(() -> parser.parse(
            extraColumn, InitialInventoryImportMode.DRY_RUN, EFFECTIVE_DATE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private InitialInventoryImportPlan parse(String csv) throws IOException {
        return parser.parse(write(csv), InitialInventoryImportMode.DRY_RUN, EFFECTIVE_DATE);
    }

    private Path write(String csv) throws IOException {
        var file = directory.resolve("snapshot-" + System.nanoTime() + ".csv");
        Files.writeString(file, csv, StandardCharsets.UTF_8);
        return file;
    }
}
