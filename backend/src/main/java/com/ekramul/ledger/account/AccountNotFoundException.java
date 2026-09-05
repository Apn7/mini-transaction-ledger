package com.ekramul.ledger.account;

/** Thrown when an operation names an account that does not exist. */
public class AccountNotFoundException extends RuntimeException {

	public AccountNotFoundException(Long accountId) {
		super("No account found with id " + accountId);
	}
}
