package com.ekramul.ledger.account;

/** The two sides of a transfer, after the money has moved. */
public record AccountPair(Account from, Account to) {
}
