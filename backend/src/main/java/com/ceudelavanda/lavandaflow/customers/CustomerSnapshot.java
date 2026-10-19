package com.ceudelavanda.lavandaflow.customers;

import java.util.UUID;

/** Immutable current contact values. Phone/email may be null; neither is an identity key. */
public record CustomerSnapshot(UUID id, String name, String phone, String email, boolean active) {
}
