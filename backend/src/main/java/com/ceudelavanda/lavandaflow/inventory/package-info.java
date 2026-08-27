@org.springframework.modulith.ApplicationModule(
    displayName = "Inventory",
    allowedDependencies = {
        "catalog",
        "suppliers",
        "shared::error"
    }
)
package com.ceudelavanda.lavandaflow.inventory;
