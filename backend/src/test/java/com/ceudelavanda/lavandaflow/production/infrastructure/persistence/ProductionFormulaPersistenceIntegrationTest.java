package com.ceudelavanda.lavandaflow.production.infrastructure.persistence;

import com.ceudelavanda.lavandaflow.TestcontainersConfiguration;
import com.ceudelavanda.lavandaflow.production.application.formula.CreateProductionFormula;
import com.ceudelavanda.lavandaflow.production.application.formula.GetProductionFormula;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaDefinitionCommand;
import com.ceudelavanda.lavandaflow.production.application.formula.ProductionFormulaIngredientCommand;
import com.ceudelavanda.lavandaflow.production.application.formula.UpdateProductionFormula;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductionFormulaPersistenceIntegrationTest {

    @Autowired
    private CreateProductionFormula createProductionFormula;

    @Autowired
    private UpdateProductionFormula updateProductionFormula;

    @Autowired
    private GetProductionFormula getProductionFormula;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistExactQuantitiesAndReplaceRequirements() {
        var outputItemId = insertCatalogItem("Issue147 Output", "MILLILITER", true);
        var firstIngredientId = insertCatalogItem("Issue147 Ingredient A", "MILLILITER", true);
        var secondIngredientId = insertCatalogItem("Issue147 Ingredient B", "GRAM", true);

        var created = createProductionFormula.execute(new ProductionFormulaDefinitionCommand(
            outputItemId,
            new BigDecimal("1000.123456"),
            List.of(new ProductionFormulaIngredientCommand(
                firstIngredientId, new BigDecimal("250.654321")
            ))
        ));

        var persisted = getProductionFormula.execute(created.id());
        assertThat(persisted.outputQuantity()).isEqualByComparingTo("1000.123456");
        assertThat(persisted.ingredients()).singleElement()
            .satisfies(ingredient -> assertThat(ingredient.quantity()).isEqualByComparingTo("250.654321"));

        updateProductionFormula.execute(created.id(), new ProductionFormulaDefinitionCommand(
            outputItemId,
            new BigDecimal("2000.000000"),
            List.of(new ProductionFormulaIngredientCommand(
                secondIngredientId, new BigDecimal("500.000000")
            ))
        ));

        var updated = getProductionFormula.execute(created.id());
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.outputQuantity()).isEqualByComparingTo("2000.000000");
        assertThat(updated.ingredients()).singleElement()
            .satisfies(ingredient -> {
                assertThat(ingredient.inventoryItemId()).isEqualTo(secondIngredientId);
                assertThat(ingredient.quantity()).isEqualByComparingTo("500.000000");
            });
    }

    @Test
    void shouldEnforceFormulaConstraintsInPostgres() {
        var outputItemId = insertCatalogItem("Issue147 DB Output", "UNIT", true);
        var ingredientItemId = insertCatalogItem("Issue147 DB Ingredient", "UNIT", true);
        var formulaId = UUID.randomUUID();

        jdbcTemplate.update(
            "insert into production_formula (id, output_inventory_item_id, output_quantity, output_unit_of_measure) values (?, ?, ?, ?)",
            formulaId, outputItemId, BigDecimal.ONE, "UNIT"
        );
        jdbcTemplate.update(
            "insert into production_formula_ingredient (formula_id, position, inventory_item_id, quantity, unit_of_measure) values (?, ?, ?, ?, ?)",
            formulaId, 0, ingredientItemId, BigDecimal.ONE, "UNIT"
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            "insert into production_formula_ingredient (formula_id, position, inventory_item_id, quantity, unit_of_measure) values (?, ?, ?, ?, ?)",
            formulaId, 1, ingredientItemId, new BigDecimal("2"), "UNIT"
        )).isInstanceOf(DataAccessException.class);

        var secondIngredientId = insertCatalogItem("Issue147 DB Ingredient Zero", "UNIT", true);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "insert into production_formula_ingredient (formula_id, position, inventory_item_id, quantity, unit_of_measure) values (?, ?, ?, ?, ?)",
            formulaId, 1, secondIngredientId, BigDecimal.ZERO, "UNIT"
        )).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "insert into production_formula (id, output_inventory_item_id, output_quantity, output_unit_of_measure) values (?, ?, ?, ?)",
            UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, "UNIT"
        )).isInstanceOf(DataAccessException.class);
    }

    private UUID insertCatalogItem(String name, String defaultUnit, boolean active) {
        var id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into inventory_item (id, name, category, default_unit, active) values (?, ?, ?, ?, ?)",
            id, name, "OTHER", defaultUnit, active
        );
        return id;
    }
}
