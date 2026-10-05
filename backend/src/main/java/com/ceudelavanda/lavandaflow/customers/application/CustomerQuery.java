package com.ceudelavanda.lavandaflow.customers.application;

/** Read port for bounded contact search. */
public interface CustomerQuery {
    CustomerPage search(CustomerSearchQuery query);
}
