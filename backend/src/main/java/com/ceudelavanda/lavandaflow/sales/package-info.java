/** Sales owns the order lifecycle; drafts have no inventory dependency or stock effects. */
@org.springframework.modulith.ApplicationModule(displayName = "Sales", allowedDependencies = {"customers", "catalog", "shared::error"})
package com.ceudelavanda.lavandaflow.sales;
