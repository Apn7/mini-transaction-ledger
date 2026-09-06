package com.ekramul.ledger.entry.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A request to move money between two accounts.
 *
 * <p>The two accounts must differ and must share a currency. Those are business rules, checked in
 * the service where both accounts are loaded — not here, where only the raw request is visible.
 */
public record TransferRequest(

		@NotNull(message = "reference is required")
		UUID reference,

		@NotNull(message = "fromAccountId is required")
		Long fromAccountId,

		@NotNull(message = "toAccountId is required")
		Long toAccountId,

		@NotNull(message = "amount is required")
		@Positive(message = "amount must be greater than zero")
		@Digits(integer = 15, fraction = 4, message = "amount supports at most 4 decimal places")
		BigDecimal amount,

		@Size(max = 255, message = "description must be at most 255 characters")
		String description) {
}
