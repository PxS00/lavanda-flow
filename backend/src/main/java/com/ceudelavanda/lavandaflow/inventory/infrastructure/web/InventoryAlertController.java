package com.ceudelavanda.lavandaflow.inventory.infrastructure.web;

import com.ceudelavanda.lavandaflow.inventory.application.GetExpirationAlerts;
import com.ceudelavanda.lavandaflow.inventory.application.query.GetExpirationAlertsQuery;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.config.InventoryAlertProperties;
import com.ceudelavanda.lavandaflow.inventory.infrastructure.web.response.ExpirationAlertsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inventory/alerts")
public class InventoryAlertController {

    private final GetExpirationAlerts getExpirationAlerts;
    private final InventoryAlertProperties inventoryAlertProperties;

    @GetMapping("/expiration")
    public ResponseEntity<ExpirationAlertsResponse> getExpirationAlerts(
        @RequestParam(required = false) Integer windowDays
    ) {
        var resolvedWindowDays = windowDays != null
            ? windowDays
            : inventoryAlertProperties.expirationWindowDays();
        var query = new GetExpirationAlertsQuery(resolvedWindowDays);
        var result = getExpirationAlerts.execute(query);

        return ResponseEntity.ok(ExpirationAlertsResponse.from(result));
    }
}
