package com.ekramul.ledger.account;

/**
 * Thrown when opening an account with a number that is already taken.
 *
 * <p>Extends {@link RuntimeException} deliberately. Spring only rolls a transaction back
 * automatically when an <em>unchecked</em> exception escapes the method. A checked exception
 * would commit the transaction on its way out.
 */
public class AccountNumberAlreadyExistsException extends RuntimeException {

	public AccountNumberAlreadyExistsException(String accountNumber) {
		super("An account with number '" + accountNumber + "' already exists");
	}
}
