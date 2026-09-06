package com.ekramul.ledger.account;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Database access for {@link Account}.
 *
 * <p>Spring Data generates the implementation at startup. Method names are parsed into queries,
 * so {@code existsByAccountNumber} becomes {@code SELECT count(*) ... WHERE account_number = ?}.
 *
 * <p>Package-private on purpose: only {@link AccountService} may reach the database for accounts.
 * Other features must go through the service, and the compiler enforces that rather than a
 * naming convention asking politely.
 */
interface AccountRepository extends JpaRepository<Account, Long> {

	boolean existsByAccountNumber(String accountNumber);

	/**
	 * Loads an account and holds a write lock on its row until the transaction ends.
	 *
	 * <p>On PostgreSQL, Hibernate renders this as {@code SELECT ... FOR NO KEY UPDATE}. That still
	 * blocks any other transaction trying to write the same row; it only stays out of the way of
	 * foreign-key checks pointing at it, which is why it is the default for a write lock.
	 *
	 * <p>A second transaction asking for the same account waits here instead of reading a balance
	 * that is about to change. Without this, two simultaneous withdrawals could both see the old
	 * balance and both succeed.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a where a.id = :id")
	Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
