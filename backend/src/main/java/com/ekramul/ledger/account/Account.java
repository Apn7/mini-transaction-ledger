package com.ekramul.ledger.account;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A ledger account.
 *
 * <p>{@code balance} is a materialized derived aggregate. The ledger entries are the canonical
 * record of what happened; this field exists so that reading a balance does not have to scan
 * the account's whole history. It may only be changed inside the same transaction as the entry
 * that causes the change.
 */
@Entity
@Table(name = "accounts")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_number", nullable = false, unique = true, length = 20)
	private String accountNumber;

	@Column(name = "owner_name", nullable = false, length = 120)
	private String ownerName;

	@Column(nullable = false, length = 3)
	private String currency;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal balance;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Required by JPA. Not for application use. */
	protected Account() {
	}

	public Account(String accountNumber, String ownerName, String currency) {
		this.accountNumber = accountNumber;
		this.ownerName = ownerName;
		this.currency = currency;
		this.balance = BigDecimal.ZERO;
	}

	/**
	 * Adds money to this account.
	 *
	 * <p>The balance is only ever changed through methods like this one, so the rules live with
	 * the data rather than being repeated by every caller.
	 *
	 * <p>Package-private on purpose: only {@link AccountService} can call it, and it always locks
	 * the row first. Other packages hold an {@code Account} but cannot move its balance.
	 */
	void credit(BigDecimal amount) {
		requirePositive(amount);
		this.balance = this.balance.add(amount);
	}

	/**
	 * Takes money out of this account.
	 *
	 * <p>Refuses to go below zero. The rule lives here, next to the balance it protects, so no
	 * caller can forget it.
	 *
	 * <p>Uses {@code compareTo} rather than {@code equals}: two BigDecimals with the same value
	 * but different scale — 10.5 and 10.5000 — are not {@code equals}, but do compare as equal.
	 */
	void debit(BigDecimal amount) {
		requirePositive(amount);

		if (this.balance.compareTo(amount) < 0) {
			throw new InsufficientBalanceException(this.accountNumber, this.balance, amount);
		}

		this.balance = this.balance.subtract(amount);
	}

	private static void requirePositive(BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) {
			throw new IllegalArgumentException("Amount must be greater than zero");
		}
	}

	public Long getId() {
		return id;
	}

	public String getAccountNumber() {
		return accountNumber;
	}

	public String getOwnerName() {
		return ownerName;
	}

	public String getCurrency() {
		return currency;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
