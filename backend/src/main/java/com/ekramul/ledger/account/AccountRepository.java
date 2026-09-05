package com.ekramul.ledger.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for {@link Account}.
 *
 * <p>Spring Data generates the implementation at startup. Method names are parsed into queries,
 * so {@code findByAccountNumber} becomes {@code SELECT ... WHERE account_number = ?}.
 *
 * <p>Package-private on purpose: only {@link AccountService} may reach the database for accounts.
 * Other features must go through the service, and the compiler enforces that rather than a
 * naming convention asking politely.
 */
interface AccountRepository extends JpaRepository<Account, Long> {

	Optional<Account> findByAccountNumber(String accountNumber);

	boolean existsByAccountNumber(String accountNumber);
}
