package com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot;

import com.ceudelavanda.lavandaflow.catalog.ProductGender;
import com.ceudelavanda.lavandaflow.inventory.domain.StockQuantityRules;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
class InitialInventorySnapshotParser {

    private static final List<String> REVISED_HEADER = List.of(
        "Nome do Perfume",
        "Genero",
        "Ml Disponiveis",
        "Expired",
        "Retirada",
        "EssenceReference",
        "ProductionTypeCode",
        "LotCode"
    );
    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern WITHDRAWAL_ADJUSTMENT = Pattern.compile("-\\s*(\\d+(?:\\.\\d+)?)ml");
    private static final Pattern EXPIRATION = Pattern.compile("\\d{2}/\\d{2}");
    private static final Pattern ESSENCE_REFERENCE = Pattern.compile("\\d{3}");
    private static final Pattern PRODUCTION_TYPE_CODE = Pattern.compile("[A-Z]{3}");
    private static final int MAX_LOT_CODE_LENGTH = 255;

    InitialInventoryImportPlan parse(
        Path file,
        InitialInventoryImportMode mode,
        LocalDate effectiveDate
    ) throws IOException {
        if (file == null || mode == null || effectiveDate == null) {
            throw new IllegalArgumentException("file, mode and effectiveDate must not be null");
        }

        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !parseCsvLine(lines.getFirst()).equals(REVISED_HEADER)) {
            throw invalidHeader();
        }

        var rows = new ArrayList<ParsedRow>();
        for (int index = 1; index < lines.size(); index++) {
            rows.add(parseRow(index, lines.get(index), effectiveDate));
        }
        validateProductIdentityGroups(rows);
        validateFragranceGenderConsistency(rows);

        var results = rows.stream().map(ParsedRow::toResult).toList();
        var report = new InitialInventoryImportReport(
            mode,
            effectiveDate,
            results.size(),
            count(results, InitialInventoryImportOutcome.CATALOG_ONLY),
            count(results, InitialInventoryImportOutcome.OPENING_STOCK),
            count(results, InitialInventoryImportOutcome.REJECTED),
            results
        );
        return new InitialInventoryImportPlan(report, buildProductPlans(rows));
    }

    private ParsedRow parseRow(int sourceRowNumber, String line, LocalDate effectiveDate) {
        var fields = parseCsvLine(line);
        if (fields.size() != REVISED_HEADER.size()) {
            return ParsedRow.rejected(sourceRowNumber, InitialInventoryImportValidationCode.MALFORMED_ROW,
                "Expected " + REVISED_HEADER.size() + " columns but found " + fields.size());
        }

        var row = new ParsedRow(sourceRowNumber);
        row.catalogName = fields.get(0).trim();
        if (row.catalogName.isBlank()) {
            row.reject(InitialInventoryImportValidationCode.BLANK_NAME, "Name must not be blank");
        }

        row.gender = parseGender(fields.get(1), row);
        row.quantity = parseQuantity(fields.get(2), row);
        if (row.quantity != null) {
            row.quantity = adjustQuantity(row.quantity, fields.get(4), row);
        }
        row.expiration = parseExpiration(fields.get(3), row);
        row.essenceReference = parseEssenceReference(fields.get(5), row);
        row.productionTypeCode = parseProductionTypeCode(fields.get(6), row);
        row.lotCode = parseLotCode(fields.get(7), row);

        if (row.quantity != null && row.quantity.signum() > 0) {
            if (row.expiration == null && row.errorCode == null) {
                row.reject(InitialInventoryImportValidationCode.EXPIRATION_REQUIRED,
                    "Expiration is required for positive stock");
            } else if (row.expiration != null && row.expiration.isBefore(effectiveDate)) {
                row.reject(InitialInventoryImportValidationCode.EXPIRATION_BEFORE_EFFECTIVE_DATE,
                    "Expiration must not precede the effective date");
            }
            if (row.lotCode == null) {
                row.reject(InitialInventoryImportValidationCode.LOT_CODE_REQUIRED,
                    "Lot code is required for positive stock");
            }
        }
        return row;
    }

    private ProductGender parseGender(String value, ParsedRow row) {
        var normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s*/\\s*", "/");
        try {
            return ProductGender.fromCode(normalized);
        } catch (IllegalArgumentException exception) {
            row.reject(InitialInventoryImportValidationCode.INVALID_GENDER, exception.getMessage());
            return null;
        }
    }

    private BigDecimal adjustQuantity(BigDecimal availableQuantity, String value, ParsedRow row) {
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            return availableQuantity;
        }

        var matcher = WITHDRAWAL_ADJUSTMENT.matcher(normalized);
        if (!matcher.matches()) {
            row.reject(InitialInventoryImportValidationCode.INVALID_WITHDRAWAL_ADJUSTMENT,
                "Withdrawal adjustment must be a negative decimal with ml suffix");
            return null;
        }

        final BigDecimal withdrawalAdjustment;
        try {
            withdrawalAdjustment = StockQuantityRules.requirePositive(
                new BigDecimal(matcher.group(1)), "withdrawal adjustment"
            ).negate();
        } catch (IllegalArgumentException exception) {
            row.reject(InitialInventoryImportValidationCode.INVALID_WITHDRAWAL_ADJUSTMENT, exception.getMessage());
            return null;
        }

        var adjustedQuantity = availableQuantity.add(withdrawalAdjustment);
        if (adjustedQuantity.signum() < 0) {
            row.reject(InitialInventoryImportValidationCode.NEGATIVE_ADJUSTED_QUANTITY,
                "Adjusted quantity must not be negative");
        }
        return adjustedQuantity;
    }

    private BigDecimal parseQuantity(String value, ParsedRow row) {
        var normalized = value.trim();
        if (!DECIMAL.matcher(normalized).matches() || normalized.startsWith("-")) {
            row.reject(InitialInventoryImportValidationCode.INVALID_QUANTITY, "Quantity must be a plain decimal");
            return null;
        }
        try {
            return StockQuantityRules.requireNonNegative(new BigDecimal(normalized), "quantity");
        } catch (IllegalArgumentException exception) {
            row.reject(InitialInventoryImportValidationCode.INVALID_QUANTITY, exception.getMessage());
            return null;
        }
    }

    private LocalDate parseExpiration(String value, ParsedRow row) {
        var normalized = value.replaceAll("\\s", "");
        if (normalized.isEmpty()) {
            return null;
        }
        if (!EXPIRATION.matcher(normalized).matches()) {
            row.reject(InitialInventoryImportValidationCode.INVALID_EXPIRATION,
                "Expiration must use MM/YY");
            return null;
        }
        try {
            return YearMonth.of(
                2000 + Integer.parseInt(normalized.substring(3)),
                Integer.parseInt(normalized.substring(0, 2))
            ).atEndOfMonth();
        } catch (DateTimeException exception) {
            row.reject(InitialInventoryImportValidationCode.INVALID_EXPIRATION, "Expiration month is invalid");
            return null;
        }
    }

    private String parseEssenceReference(String value, ParsedRow row) {
        var normalized = value.trim();
        if (!ESSENCE_REFERENCE.matcher(normalized).matches() || normalized.equals("000")) {
            row.reject(InitialInventoryImportValidationCode.INVALID_ESSENCE_REFERENCE,
                "Essence reference must be 001 through 999");
        }
        return normalized;
    }

    private String parseProductionTypeCode(String value, ParsedRow row) {
        var normalized = value.trim();
        if (!PRODUCTION_TYPE_CODE.matcher(normalized).matches()) {
            row.reject(InitialInventoryImportValidationCode.INVALID_PRODUCTION_TYPE_CODE,
                "Production type code must be exactly three uppercase letters");
        }
        return normalized;
    }

    private String parseLotCode(String value, ParsedRow row) {
        var normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_LOT_CODE_LENGTH) {
            row.reject(InitialInventoryImportValidationCode.INVALID_LOT_CODE,
                "Lot code must not exceed 255 characters");
        }
        return normalized;
    }

    private void validateProductIdentityGroups(List<ParsedRow> rows) {
        var groups = new LinkedHashMap<ProductIdentity, List<ParsedRow>>();
        rows.stream()
            .filter(ParsedRow::hasValidIdentity)
            .forEach(row -> groups.computeIfAbsent(row.identity(), ignored -> new ArrayList<>()).add(row));

        groups.values().forEach(group -> {
            var names = group.stream().map(row -> row.catalogName).distinct().count();
            var genders = group.stream().filter(row -> row.gender != null).map(row -> row.gender).distinct().count();
            if (names > 1 || genders > 1) {
                group.forEach(row -> row.reject(InitialInventoryImportValidationCode.CONFLICTING_PRODUCT_METADATA,
                    "Rows for one product identity must use identical name and gender"));
            }

            var lots = new HashMap<String, List<ParsedRow>>();
            group.stream()
                .filter(row -> row.quantity != null && row.quantity.signum() > 0 && row.hasValidLotCode())
                .forEach(row -> lots.computeIfAbsent(row.lotCode, ignored -> new ArrayList<>()).add(row));
            lots.values().stream().filter(duplicates -> duplicates.size() > 1).forEach(duplicates ->
                duplicates.forEach(row -> row.reject(InitialInventoryImportValidationCode.DUPLICATE_LOT_CODE,
                    "Lot code must be unique within one imported product identity"))
            );
        });
    }

    private void validateFragranceGenderConsistency(List<ParsedRow> rows) {
        var groups = new LinkedHashMap<String, List<ParsedRow>>();
        rows.stream()
            .filter(row -> isValidEssenceReference(row.essenceReference) && row.gender != null)
            .forEach(row -> groups.computeIfAbsent(row.essenceReference, ignored -> new ArrayList<>()).add(row));

        groups.values().forEach(group -> {
            if (group.stream().map(row -> row.gender).distinct().count() > 1) {
                group.forEach(row -> row.reject(InitialInventoryImportValidationCode.CONFLICTING_FRAGRANCE_GENDER,
                    "Rows sharing one essence reference must use the same gender"));
            }
        });
    }

    private List<InitialInventoryProductPlan> buildProductPlans(List<ParsedRow> rows) {
        var groups = new LinkedHashMap<ProductIdentity, List<ParsedRow>>();
        rows.stream()
            .filter(row -> row.errorCode == null)
            .forEach(row -> groups.computeIfAbsent(row.identity(), ignored -> new ArrayList<>()).add(row));

        return groups.values().stream().map(group -> {
            var first = group.getFirst();
            return new InitialInventoryProductPlan(
                first.catalogName,
                first.gender,
                first.essenceReference,
                first.productionTypeCode,
                group.stream().map(ParsedRow::toValidRow).toList()
            );
        }).toList();
    }

    private static boolean isValidEssenceReference(String value) {
        return value != null && ESSENCE_REFERENCE.matcher(value).matches() && !value.equals("000");
    }

    private static boolean isValidProductionTypeCode(String value) {
        return value != null && PRODUCTION_TYPE_CODE.matcher(value).matches();
    }

    private static IllegalArgumentException invalidHeader() {
        return new IllegalArgumentException(
            "CSV header must be exactly: " + String.join(",", REVISED_HEADER)
        );
    }

    private static int count(
        List<InitialInventoryImportRowResult> rows,
        InitialInventoryImportOutcome outcome
    ) {
        return Math.toIntExact(rows.stream().filter(row -> row.outcome() == outcome).count());
    }

    private static List<String> parseCsvLine(String line) {
        return List.of(line.split(",", -1));
    }

    private record ProductIdentity(String productionTypeCode, String essenceReference) {
    }

    private static final class ParsedRow {
        private final int sourceRowNumber;
        private String catalogName;
        private ProductGender gender;
        private BigDecimal quantity;
        private LocalDate expiration;
        private String essenceReference;
        private String productionTypeCode;
        private String lotCode;
        private InitialInventoryImportValidationCode errorCode;
        private String errorReason;

        private ParsedRow(int sourceRowNumber) {
            this.sourceRowNumber = sourceRowNumber;
        }

        private static ParsedRow rejected(
            int sourceRowNumber,
            InitialInventoryImportValidationCode code,
            String reason
        ) {
            var row = new ParsedRow(sourceRowNumber);
            row.reject(code, reason);
            return row;
        }

        private boolean hasValidIdentity() {
            return catalogName != null && !catalogName.isBlank()
                && isValidEssenceReference(essenceReference)
                && isValidProductionTypeCode(productionTypeCode);
        }

        private boolean hasValidLotCode() {
            return lotCode != null && lotCode.length() <= MAX_LOT_CODE_LENGTH;
        }

        private ProductIdentity identity() {
            return new ProductIdentity(productionTypeCode, essenceReference);
        }

        private void reject(InitialInventoryImportValidationCode code, String reason) {
            if (errorCode == null) {
                errorCode = code;
                errorReason = reason;
            }
        }

        private InitialInventoryImportRowResult toResult() {
            var outcome = errorCode != null
                ? InitialInventoryImportOutcome.REJECTED
                : quantity.signum() == 0
                    ? InitialInventoryImportOutcome.CATALOG_ONLY
                    : InitialInventoryImportOutcome.OPENING_STOCK;
            return new InitialInventoryImportRowResult(
                sourceRowNumber,
                catalogName,
                gender == null ? null : gender.code(),
                essenceReference,
                productionTypeCode,
                lotCode,
                quantity,
                expiration,
                outcome,
                errorCode,
                errorReason
            );
        }

        private ValidInitialInventoryRow toValidRow() {
            return new ValidInitialInventoryRow(lotCode, quantity, expiration);
        }
    }
}

record InitialInventoryImportPlan(
    InitialInventoryImportReport report,
    List<InitialInventoryProductPlan> products
) {
}

record InitialInventoryProductPlan(
    String catalogName,
    ProductGender gender,
    String essenceReference,
    String productionTypeCode,
    List<ValidInitialInventoryRow> rows
) {
}

record ValidInitialInventoryRow(String lotCode, BigDecimal quantity, LocalDate expiration) {
}
