package com.ceudelavanda.lavandaflow.sales.application;

import com.ceudelavanda.lavandaflow.sales.domain.Order;
import java.util.List;

public record OrderPage(List<Order> content, int page, int size, long totalElements, int totalPages) {}
