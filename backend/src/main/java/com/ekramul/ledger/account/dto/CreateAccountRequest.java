package com.ekramul.ledger.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What a client may send when opening an account.
 *
 * <p>Deliberately does not include a balance. A new account starts at zero, and money can only
 * arrive through a ledger entry — never by being asserted in a request.
 */
public record CreateAccountRequest(

		@NotBlank(message = "accountNumber is required")
		@Size(max = 20, message = "accountNumber must be at most 20 characters")
		String accountNumber,

		@NotBlank(message = "ownerName is required")
		@Size(max = 120, message = "ownerName must be at most 120 characters")
		String ownerName,

		@NotBlank(message = "currency is required")
		@Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code, e.g. BDT")
		String currency) {
}
