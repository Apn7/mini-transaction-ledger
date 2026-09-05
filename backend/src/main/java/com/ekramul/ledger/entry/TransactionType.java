package com.ekramul.ledger.entry;

/** The kind of financial event that produced a set of ledger entries. */
public enum TransactionType {

	/** Money entering the ledger from outside. One CREDIT entry. */
	DEPOSIT,

	/** Money leaving the ledger. One DEBIT entry. */
	WITHDRAWAL,

	/** Money moving between two accounts. One DEBIT and one CREDIT, netting to zero. */
	TRANSFER
}
