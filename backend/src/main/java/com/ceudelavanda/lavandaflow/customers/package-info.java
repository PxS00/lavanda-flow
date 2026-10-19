/** Customer contact ownership; other modules consume only the root-package lookup and values. */
@org.springframework.modulith.ApplicationModule(
    displayName = "Customers",
    allowedDependencies = {"shared::error"}
)
package com.ceudelavanda.lavandaflow.customers;
