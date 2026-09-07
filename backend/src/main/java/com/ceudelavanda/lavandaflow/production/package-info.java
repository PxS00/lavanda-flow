@org.springframework.modulith.ApplicationModule(
    displayName = "Production",
    allowedDependencies = {
        "catalog",
        "inventory",
        "shared::error"
    }
)
package com.ceudelavanda.lavandaflow.production;
