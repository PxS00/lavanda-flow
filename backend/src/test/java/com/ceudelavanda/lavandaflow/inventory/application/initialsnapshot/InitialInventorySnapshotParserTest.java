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
    private static final String HEADER =
        "Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada,EssenceReference,ProductionTypeCode,LotCode";

    @TempDir
    Path directory;

    private final InitialInventorySnapshotParser parser = new InitialInventorySnapshotParser();

    @Test
    void shouldNormalizeRevisedSourceAndExposeStableMetadata() throws IOException {
        var plan = parse("""
            %s
              Serena  , f / c ,10.5,09/2 7,-0.500001ml,014,PFM, PFM-014-001-09-2026 
            Catalog only,M,0,,,021,PFM,
            """.formatted(HEADER).replace("\n", "\r\n"));

        assertThat(plan.report().totalRowCount()).isEqualTo(2);
        assertThat(plan.report().openingStockCount()).isEqualTo(1);
        assertThat(plan.report().catalogOnlyCount()).isEqualTo(1);
        assertThat(plan.report().rejectedCount()).isZero();
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::sourceRowNumber)
            .containsExactly(1, 2);
        assertThat(plan.report().rows().getFirst()).satisfies(row -> {
            assertThat(row.catalogName()).isEqualTo("Serena");
            assertThat(row.gender()).isEqualTo("F/C");
            assertThat(row.essenceReference()).isEqualTo("014");
            assertThat(row.productionTypeCode()).isEqualTo("PFM");
            assertThat(row.lotCode()).isEqualTo("PFM-014-001-09-2026");
            assertThat(row.quantity()).isEqualByComparingTo("9.999999");
            assertThat(row.expiration()).isEqualTo(LocalDate.of(2027, 9, 30));
        });
        assertThat(plan.products()).hasSize(2);
    }

    @Test
    void shouldRejectLegacyHeadersAndAnyDifferentColumnShape() throws IOException {
        var legacyFour = write("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired
            Item,F,1,09/27
            """);
        var legacyFive = write("""
            Nome do Perfume,Genero,Ml Disponiveis,Expired,Retirada
            Item,F,1,09/27,
            """);
        var reordered = write("""
            Nome do Perfume,Genero,Ml Disponiveis,Retirada,Expired,EssenceReference,ProductionTypeCode,LotCode
            Item,F,1,,09/27,014,PFM,L1
            """);
        var extra = write(HEADER + ",Unexpected\nItem,F,1,09/27,,014,PFM,L1,x\n");

        for (var file : new Path[]{legacyFour, legacyFive, reordered, extra}) {
            assertThatThrownBy(() -> parser.parse(file, InitialInventoryImportMode.DRY_RUN, EFFECTIVE_DATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CSV header must be exactly: " + HEADER);
        }
    }

    @Test
    void shouldRejectInvalidGenderValues() throws IOException {
        var plan = parse("""
            %s
            Blank,,1,09/27,,014,PFM,L1
            Unsupported,X,1,09/27,,015,PFM,L2
            """.formatted(HEADER));

        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsExactly(
                InitialInventoryImportValidationCode.INVALID_GENDER,
                InitialInventoryImportValidationCode.INVALID_GENDER
            );
    }

    @Test
    void shouldRejectInvalidEssenceReferences() throws IOException {
        var plan = parse("""
            %s
            Reserved,F,1,09/27,,000,PFM,L1
            Short,F,1,09/27,,14,PFM,L2
            Alpha,F,1,09/27,,A14,PFM,L3
            """.formatted(HEADER));

        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsOnly(InitialInventoryImportValidationCode.INVALID_ESSENCE_REFERENCE);
    }

    @Test
    void shouldRejectProductionTypeCodesThatAreNotExactlyUppercaseAscii() throws IOException {
        var plan = parse("""
            %s
            Lower,F,1,09/27,,014,pfm,L1
            Short,F,1,09/27,,015,PF,L2
            Numeric,F,1,09/27,,016,P1M,L3
            """.formatted(HEADER));

        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsOnly(InitialInventoryImportValidationCode.INVALID_PRODUCTION_TYPE_CODE);
    }

    @Test
    void shouldRequireLotOnlyForPositiveAdjustedStock() throws IOException {
        var plan = parse("""
            %s
            Positive,F,1,09/27,,014,PFM,
            Zero blank,M,0,,,015,PFM,
            Zero with source lot,C,0,,,016,PFM,LEGACY-LOT
            """.formatted(HEADER));

        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsExactly(InitialInventoryImportValidationCode.LOT_CODE_REQUIRED, null, null);
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::outcome)
            .containsExactly(
                InitialInventoryImportOutcome.REJECTED,
                InitialInventoryImportOutcome.CATALOG_ONLY,
                InitialInventoryImportOutcome.CATALOG_ONLY
            );
        assertThat(plan.report().rows().get(2).lotCode()).isEqualTo("LEGACY-LOT");
    }

    @Test
    void shouldRejectLotCodeLongerThanBatchLimit() throws IOException {
        var plan = parse(HEADER + "\nItem,F,1,09/27,,014,PFM," + "L".repeat(256) + "\n");

        assertThat(plan.report().rows().getFirst().validationCode())
            .isEqualTo(InitialInventoryImportValidationCode.INVALID_LOT_CODE);
    }

    @Test
    void shouldPreserveWithdrawalAdjustmentSemantics() throws IOException {
        var plan = parse("""
            %s
            Adjusted,F,80,09/27,-50ml,014,PFM,L1
            Spaced,M,80,10/27,- 50ml,015,PFM,L2
            Decimal,C,10.5,11/27,-0.500001ml,016,PFM,L3
            Catalog only,F/C,50,,-50ml,017,PFM,
            """.formatted(HEADER));

        assertThat(plan.report().openingStockCount()).isEqualTo(3);
        assertThat(plan.report().catalogOnlyCount()).isEqualTo(1);
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::quantity)
            .containsExactly(
                new BigDecimal("30.000000"),
                new BigDecimal("30.000000"),
                new BigDecimal("9.999999"),
                new BigDecimal("0.000000")
            );
    }

    @Test
    void shouldRejectInvalidWithdrawalAndNegativeAdjustedQuantity() throws IOException {
        var plan = parse("""
            %s
            Positive,F,80,09/27,50ml,014,PFM,L1
            Malformed,M,80,09/27,-50liters,015,PFM,L2
            Too precise,C,80,09/27,-0.0000001ml,016,PFM,L3
            Below zero,F/C,10,09/27,-11ml,017,PFM,L4
            """.formatted(HEADER));

        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsExactly(
                InitialInventoryImportValidationCode.INVALID_WITHDRAWAL_ADJUSTMENT,
                InitialInventoryImportValidationCode.INVALID_WITHDRAWAL_ADJUSTMENT,
                InitialInventoryImportValidationCode.INVALID_WITHDRAWAL_ADJUSTMENT,
                InitialInventoryImportValidationCode.NEGATIVE_ADJUSTED_QUANTITY
            );
    }

    @Test
    void shouldPreserveExactQuantityAndExpirationValidation() throws IOException {
        var plan = parse("""
            %s
            Valid,F,1.000001,09/27,,014,PFM,L1
            Too precise,M,1.0000001,09/27,,015,PFM,L2
            Positive blank,C,2,,,016,PFM,L3
            Already expired,M/C,2,08/26,,017,PFM,L4
            Bad expiration,F/C,2,13/27,,018,PFM,L5
            Exponent,F,1e2,09/27,,019,PFM,L6
            Negative zero,F,-0,09/27,,020,PFM,L7
            """.formatted(HEADER));

        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsExactly(
                null,
                InitialInventoryImportValidationCode.INVALID_QUANTITY,
                InitialInventoryImportValidationCode.EXPIRATION_REQUIRED,
                InitialInventoryImportValidationCode.EXPIRATION_BEFORE_EFFECTIVE_DATE,
                InitialInventoryImportValidationCode.INVALID_EXPIRATION,
                InitialInventoryImportValidationCode.INVALID_QUANTITY,
                InitialInventoryImportValidationCode.INVALID_QUANTITY
            );
    }

    @Test
    void shouldGroupRepeatedProductIdentityIntoOneProductWithDistinctLots() throws IOException {
        var plan = parse("""
            %s
            Serena,F,10,09/27,,014,PFM,L1
            Serena,F,20,10/27,,014,PFM,L2
            """.formatted(HEADER));

        assertThat(plan.report().rejectedCount()).isZero();
        assertThat(plan.products()).singleElement().satisfies(product -> {
            assertThat(product.catalogName()).isEqualTo("Serena");
            assertThat(product.essenceReference()).isEqualTo("014");
            assertThat(product.productionTypeCode()).isEqualTo("PFM");
            assertThat(product.rows()).extracting(ValidInitialInventoryRow::lotCode).containsExactly("L1", "L2");
        });
    }

    @Test
    void shouldRejectDuplicateLotWithinSameProductIdentity() throws IOException {
        var plan = parse("""
            %s
            Serena,F,10,09/27,,014,PFM,L1
            Serena,F,20,10/27,,014,PFM,L1
            """.formatted(HEADER));

        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsOnly(InitialInventoryImportValidationCode.DUPLICATE_LOT_CODE);
        assertThat(plan.products()).isEmpty();
    }

    @Test
    void shouldRejectConflictingNameForSameProductIdentity() throws IOException {
        var plan = parse("""
            %s
            Serena,F,10,09/27,,014,PFM,L1
            Serena Premium,F,20,10/27,,014,PFM,L2
            """.formatted(HEADER));

        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsOnly(InitialInventoryImportValidationCode.CONFLICTING_PRODUCT_METADATA);
    }

    @Test
    void shouldRejectConflictingGenderForSameProductIdentity() throws IOException {
        var plan = parse("""
            %s
            Serena,F,10,09/27,,014,PFM,L1
            Serena,M,20,10/27,,014,PFM,L2
            """.formatted(HEADER));

        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsOnly(InitialInventoryImportValidationCode.CONFLICTING_PRODUCT_METADATA);
    }

    @Test
    void shouldRejectConflictingGenderForSameFragranceAcrossProductionTypes() throws IOException {
        var plan = parse("""
            %s
            Serena Perfume,F,10,09/27,,014,PFM,L1
            Serena Body Splash,M,20,10/27,,014,BDS,L2
            """.formatted(HEADER));

        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsOnly(InitialInventoryImportValidationCode.CONFLICTING_FRAGRANCE_GENDER);
    }

    @Test
    void shouldAllowEqualDisplayNamesAcrossDistinctStableIdentities() throws IOException {
        var plan = parse("""
            %s
            Shared Name,F,10,09/27,,014,PFM,L1
            Shared Name,F,20,10/27,,015,PFM,L1
            """.formatted(HEADER));

        assertThat(plan.report().rejectedCount()).isZero();
        assertThat(plan.products()).hasSize(2);
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::catalogName)
            .containsExactly("Shared Name", "Shared Name");
    }

    @Test
    void shouldAllowSameLotCodeAcrossDistinctProductIdentities() throws IOException {
        var plan = parse("""
            %s
            First,F,10,09/27,,014,PFM,LOT-001
            Second,M,20,10/27,,015,PFM,LOT-001
            """.formatted(HEADER));

        assertThat(plan.report().rejectedCount()).isZero();
        assertThat(plan.products()).hasSize(2);
    }

    @Test
    void shouldAccumulateMalformedRowsInStableSourceOrder() throws IOException {
        var plan = parse("""
            %s
            Valid,F,1,09/27,,014,PFM,L1
            Bad columns,F,2
            Valid zero,C,0,,,015,BDS,
            """.formatted(HEADER));

        assertThat(plan.report().totalRowCount()).isEqualTo(3);
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::sourceRowNumber)
            .containsExactly(1, 2, 3);
        assertThat(plan.report().rows()).extracting(InitialInventoryImportRowResult::validationCode)
            .containsExactly(null, InitialInventoryImportValidationCode.MALFORMED_ROW, null);
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
