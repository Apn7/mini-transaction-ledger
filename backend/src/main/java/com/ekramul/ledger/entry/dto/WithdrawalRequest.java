package com.ekramul.ledger.entry.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A request to take money out of an account.
 *
 * <p>Identical in shape to {@link DepositRequest} today, but kept separate on purpose: a request
 * DTO is an API contract, and sharing one would mean neither endpoint could change without
 * affecting the other.
 */
public record WithdrawalRequest(

		@NotNull(message = "reference is required")
		UUID reference,

		@NotNull(message = "amount is required")
		@Positive(message = "amount must be greater than zero")
		@Digits(integer = 15, fraction = 4, message = "amount supports at most 4 decimal places")
		BigDecimal amount,

		@Size(max = 255, message = "description must be at most 255 characters")
		String description) {
}
