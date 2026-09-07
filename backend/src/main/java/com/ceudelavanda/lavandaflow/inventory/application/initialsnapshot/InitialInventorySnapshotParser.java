package com.ceudelavanda.lavandaflow.inventory.application.initialsnapshot;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
class InitialInventorySnapshotParser {

    private static final List<String> ORIGINAL_HEADER = List.of(
        "Nome do Perfume", "Genero", "Ml Disponiveis", "Expired"
    );
    private static final List<String> FINAL_SNAPSHOT_HEADER = List.of(
        "Nome do Perfume", "Genero", "Ml Disponiveis", "Expired", "Retirada"
    );
    private static final Set<String> REFERENCES = Set.of("F", "M", "C", "M/C", "F/C");
    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern WITHDRAWAL_ADJUSTMENT = Pattern.compile("-\\s*(\\d+(?:\\.\\d+)?)ml");
    private static final Pattern EXPIRATION = Pattern.compile("\\d{2}/\\d{2}");

    InitialInventoryImportPlan parse(
        Path file,
        InitialInventoryImportMode mode,
        LocalDate effectiveDate
    ) throws IOException {
        if (file == null || mode == null || effectiveDate == null) {
            throw new IllegalArgumentException("file, mode and effectiveDate must not be null");
        }

        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw invalidHeader();
        }
        var header = parseCsvLine(lines.getFirst());
        var hasWithdrawalAdjustment = header.equals(FINAL_SNAPSHOT_HEADER);
        if (!header.equals(ORIGINAL_HEADER) && !hasWithdrawalAdjustment) {
            throw invalidHeader();
        }

        var rows = new ArrayList<ParsedRow>();
        for (int index = 1; index < lines.size(); index++) {
            rows.add(parseRow(index, lines.get(index), effectiveDate, hasWithdrawalAdjustment));
        }
        resolveDuplicateNames(rows);

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
        return new InitialInventoryImportPlan(report, rows.stream()
            .filter(row -> row.errorCode == null)
            .map(row -> new ValidInitialInventoryRow(row.catalogName, row.quantity, row.expiration))
            .toList());
    }

    private ParsedRow parseRow(
        int sourceRowNumber,
        String line,
        LocalDate effectiveDate,
        boolean hasWithdrawalAdjustment
    ) {
        var fields = parseCsvLine(line);
        var expectedColumnCount = hasWithdrawalAdjustment ? FINAL_SNAPSHOT_HEADER.size() : ORIGINAL_HEADER.size();
        if (fields.size() != expectedColumnCount) {
            return ParsedRow.rejected(sourceRowNumber, InitialInventoryImportValidationCode.MALFORMED_ROW,
                "Expected " + expectedColumnCount + " columns but found " + fields.size());
        }

        var row = new ParsedRow(sourceRowNumber);
        row.catalogName = fields.get(0).trim();
        if (row.catalogName.isBlank()) {
            row.reject(InitialInventoryImportValidationCode.BLANK_NAME, "Name must not be blank");
        }

        row.reference = normalizeReference(fields.get(1));
        if (!REFERENCES.contains(row.reference)) {
            row.reject(InitialInventoryImportValidationCode.INVALID_REFERENCE, "Unsupported legacy reference");
        }

        row.quantity = parseQuantity(fields.get(2), row);
        if (row.quantity != null && hasWithdrawalAdjustment) {
            row.quantity = adjustQuantity(row.quantity, fields.get(4), row);
        }
        row.expiration = parseExpiration(fields.get(3), row);
        if (row.quantity != null && row.quantity.signum() > 0) {
            if (row.expiration == null && row.errorCode == null) {
                row.reject(InitialInventoryImportValidationCode.EXPIRATION_REQUIRED,
                    "Expiration is required for positive stock");
            } else if (row.expiration != null && row.expiration.isBefore(effectiveDate)) {
                row.reject(InitialInventoryImportValidationCode.EXPIRATION_BEFORE_EFFECTIVE_DATE,
                    "Expiration must not precede the effective date");
            }
        }
        return row;
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

    private void resolveDuplicateNames(List<ParsedRow> rows) {
        var groups = new HashMap<String, List<ParsedRow>>();
        rows.stream()
            .filter(row -> row.catalogName != null && !row.catalogName.isBlank())
            .forEach(row -> groups.computeIfAbsent(row.catalogName.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                .add(row));

        groups.values().stream().filter(group -> group.size() > 1).forEach(group -> {
            var references = new HashSet<String>();
            var resolvable = group.stream().allMatch(row -> REFERENCES.contains(row.reference)
                && references.add(row.reference));
            if (resolvable) {
                group.forEach(row -> row.catalogName = row.catalogName + " (" + row.reference + ")");
            } else {
                group.forEach(row -> row.reject(InitialInventoryImportValidationCode.DUPLICATE_NAME,
                    "Duplicate name cannot be uniquely disambiguated by legacy reference"));
            }
        });
    }

    private static String normalizeReference(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s*/\\s*", "/");
    }

    private static IllegalArgumentException invalidHeader() {
        return new IllegalArgumentException(
            "CSV header must be exactly: " + String.join(",", ORIGINAL_HEADER)
                + " or " + String.join(",", FINAL_SNAPSHOT_HEADER)
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

    private static final class ParsedRow {
        private final int sourceRowNumber;
        private String catalogName;
        private String reference;
        private BigDecimal quantity;
        private LocalDate expiration;
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
                sourceRowNumber, catalogName, reference, quantity, expiration, outcome, errorCode, errorReason
            );
        }
    }
}

record InitialInventoryImportPlan(
    InitialInventoryImportReport report,
    List<ValidInitialInventoryRow> validRows
) {
}

record ValidInitialInventoryRow(String catalogName, BigDecimal quantity, LocalDate expiration) {
}
