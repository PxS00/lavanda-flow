package com.ceudelavanda.lavandaflow.customers.infrastructure.web.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** Complete contact fields for create/update; omitted optional values clear the contact method. */
public record CustomerRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 160, description = "Required nonblank name; surrounding whitespace is trimmed") String name,
    @Schema(description = "Optional phone: leading + and 7–15 digits after removing spaces, parentheses, periods and hyphens", example = "+55 (11) 99999-1234") String phone,
    @Schema(maxLength = 254, format = "email", description = "Optional standard email address; blank becomes null") String email
) {
}
