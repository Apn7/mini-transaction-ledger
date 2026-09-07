package com.ekramul.ledger.entry;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for {@link LedgerEntry}. */
interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

	/**
	 * An account's entries, oldest first — the order a statement is printed in.
	 *
	 * <p>{@code @EntityGraph} joins the parent {@code transactions} row into this one query.
	 * Without it, {@code EntryResponse.from} touches the LAZY {@code transaction} on every entry
	 * and each touch is its own SELECT — one query for the list, then one per row.
	 *
	 * <p>The association stays LAZY, which is the right default. The fetch plan is chosen here, by
	 * the read that actually needs the parent, rather than being forced on every other query.
	 */
	@EntityGraph(attributePaths = "transaction")
	List<LedgerEntry> findByAccountIdOrderByIdAsc(Long accountId);
}
