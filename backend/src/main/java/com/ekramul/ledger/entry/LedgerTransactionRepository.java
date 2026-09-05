package com.ekramul.ledger.entry;

import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for {@link LedgerTransaction}. */
interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {
}
