package com.ceudelavanda.lavandaflow.sales.application;

/** Bounded aggregate read port. */
public interface OrderQuery { OrderPage search(OrderSearchQuery query); }
