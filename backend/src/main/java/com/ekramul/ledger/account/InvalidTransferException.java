package com.ekramul.ledger.account;

/** Thrown when a transfer is well formed but not allowed — same account, mismatched currency. */
public class InvalidTransferException extends RuntimeException {

	public InvalidTransferException(String message) {
		super(message);
	}
}
