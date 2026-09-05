package com.ekramul.ledger.account;

import java.math.BigDecimal;

/** Thrown when a withdrawal or transfer would take an account below zero. */
public class InsufficientBalanceException extends RuntimeException {

	public InsufficientBalanceException(String accountNumber, BigDecimal balance, BigDecimal requested) {
		super("Account " + accountNumber + " has a balance of " + balance
				+ ", which is less than the requested " + requested);
	}
}
