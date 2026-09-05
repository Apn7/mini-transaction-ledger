package com.ekramul.ledger.entry;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for {@link LedgerEntry}. */
interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

	/** An account's entries, oldest first — the order a statement is printed in. */
	List<LedgerEntry> findByAccountIdOrderByIdAsc(Long accountId);
}
