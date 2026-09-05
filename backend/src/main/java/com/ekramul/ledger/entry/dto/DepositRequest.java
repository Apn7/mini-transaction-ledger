package com.ekramul.ledger.entry.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A request to put money into an account.
 *
 * @param reference   the caller's own id for this deposit. Sending it twice cannot create two
 *                    deposits, because the column is unique. This is the idempotency key.
 * @param amount      how much. Declared as {@link BigDecimal} so the value never passes through
 *                    a floating point type and loses precision.
 * @param description free text for the statement line.
 */
public record DepositRequest(

		@NotNull(message = "reference is required")
		UUID reference,

		@NotNull(message = "amount is required")
		@Positive(message = "amount must be greater than zero")
		@Digits(integer = 15, fraction = 4, message = "amount supports at most 4 decimal places")
		BigDecimal amount,

		@Size(max = 255, message = "description must be at most 255 characters")
		String description) {
}
