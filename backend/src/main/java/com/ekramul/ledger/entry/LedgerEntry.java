package com.ekramul.ledger.entry;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.ekramul.ledger.account.Account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One movement of money on one account.
 *
 * <p><strong>Append-only.</strong> There are no setters, because a posted entry is never
 * changed. A mistake is corrected by posting a reversing entry, so the history always says what
 * actually happened.
 *
 * <p>{@code amount} is always positive; {@link Direction} says which way the money moved.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * {@code LAZY} on purpose. {@code @ManyToOne} defaults to {@code EAGER}, which quietly loads
	 * the parent row on every query and is a common source of unnecessary joins.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "transaction_id", nullable = false)
	private LedgerTransaction transaction;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 6)
	private Direction direction;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	/** The account's balance immediately after this entry was applied. */
	@Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
	private BigDecimal balanceAfter;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Required by JPA. Not for application use. */
	protected LedgerEntry() {
	}

	public LedgerEntry(LedgerTransaction transaction, Account account, Direction direction,
			BigDecimal amount, BigDecimal balanceAfter) {
		this.transaction = transaction;
		this.account = account;
		this.direction = direction;
		this.amount = amount;
		this.balanceAfter = balanceAfter;
	}

	public Long getId() {
		return id;
	}

	public LedgerTransaction getTransaction() {
		return transaction;
	}

	public Account getAccount() {
		return account;
	}

	public Direction getDirection() {
		return direction;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
