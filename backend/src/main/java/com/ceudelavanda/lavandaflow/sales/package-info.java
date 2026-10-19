/** Sales owns the order lifecycle; confirmation calls only the public inventory API; drafts have no stock effects. */
@org.springframework.modulith.ApplicationModule(displayName = "Sales", allowedDependencies = {"customers", "catalog", "inventory", "shared::error"})
package com.ceudelavanda.lavandaflow.sales;
