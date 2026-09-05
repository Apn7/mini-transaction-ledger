package com.ekramul.ledger.entry;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One financial event. A deposit, a withdrawal, or a transfer.
 *
 * <p>Named {@code LedgerTransaction} rather than {@code Transaction} so it is never confused with
 * a database transaction in conversation or in imports.
 *
 * <p>{@code reference} is the idempotency key. It is supplied by the caller and unique, so a
 * retried request cannot record the same event twice.
 */
@Entity
@Table(name = "transactions")
public class LedgerTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private UUID reference;

	/**
	 * {@code STRING} on purpose. The default is {@code ORDINAL}, which stores the enum's position
	 * as a number — reorder the enum later and every existing row silently changes meaning.
	 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 12)
	private TransactionType type;

	@Column(length = 255)
	private String description;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Required by JPA. Not for application use. */
	protected LedgerTransaction() {
	}

	public LedgerTransaction(UUID reference, TransactionType type, String description) {
		this.reference = reference;
		this.type = type;
		this.description = description;
	}

	public Long getId() {
		return id;
	}

	public UUID getReference() {
		return reference;
	}

	public TransactionType getType() {
		return type;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
