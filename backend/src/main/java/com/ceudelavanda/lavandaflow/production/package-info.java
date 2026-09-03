@org.springframework.modulith.ApplicationModule(
    displayName = "Production",
    allowedDependencies = {
        "catalog",
        "shared::error"
    }
)
package com.ceudelavanda.lavandaflow.production;
