package com.ekramul.ledger.entry.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ekramul.ledger.entry.Direction;
import com.ekramul.ledger.entry.LedgerEntry;

/** One line of a statement: what moved, which way, and the balance it left behind. */
public record EntryResponse(
		Long entryId,
		UUID reference,
		Long accountId,
		Direction direction,
		BigDecimal amount,
		BigDecimal balanceAfter,
		String description,
		Instant createdAt) {

	public static EntryResponse from(LedgerEntry entry) {
		return new EntryResponse(
				entry.getId(),
				entry.getTransaction().getReference(),
				entry.getAccount().getId(),
				entry.getDirection(),
				entry.getAmount(),
				entry.getBalanceAfter(),
				entry.getTransaction().getDescription(),
				entry.getCreatedAt());
	}
}
