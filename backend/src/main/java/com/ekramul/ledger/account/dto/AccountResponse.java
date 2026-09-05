package com.ekramul.ledger.account.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ekramul.ledger.account.Account;

/** What the API returns for an account. Shaped for the client, not for the database. */
public record AccountResponse(
		Long id,
		String accountNumber,
		String ownerName,
		String currency,
		BigDecimal balance,
		Instant createdAt) {

	public static AccountResponse from(Account account) {
		return new AccountResponse(
				account.getId(),
				account.getAccountNumber(),
				account.getOwnerName(),
				account.getCurrency(),
				account.getBalance(),
				account.getCreatedAt());
	}
}
