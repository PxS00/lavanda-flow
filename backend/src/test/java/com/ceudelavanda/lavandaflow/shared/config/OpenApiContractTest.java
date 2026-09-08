package com.ceudelavanda.lavandaflow.shared.config;

import com.ceudelavanda.lavandaflow.inventory.application.receipt.RegisterStockReceipt;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.StockReceiptController;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = StockReceiptController.class,
    properties = "springdoc.api-docs.enabled=true"
)
@Import({ClockConfig.class, ExactDecimalJsonConfiguration.class, OpenApiConfiguration.class})
@ImportAutoConfiguration({
    SpringDocConfiguration.class,
    SpringDocConfigProperties.class,
    SpringDocWebMvcConfiguration.class
})
@WithMockUser
class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterStockReceipt registerStockReceipt;

    @Test
    void shouldDescribeBigDecimalResponsesAsDecimalStrings() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.schemas.RegisterStockReceiptResponse.properties.quantity.type")
                .value("string"))
            .andExpect(jsonPath("$.components.schemas.RegisterStockReceiptResponse.properties.quantity.format")
                .value("decimal"));
    }
}
