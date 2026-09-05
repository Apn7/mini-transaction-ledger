package com.ekramul.ledger.account;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ekramul.ledger.account.dto.AccountResponse;
import com.ekramul.ledger.account.dto.CreateAccountRequest;

/** Business rules for accounts. Holds no HTTP concerns and no SQL. */
@Service
public class AccountService {

	private final AccountRepository accountRepository;

	public AccountService(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	/**
	 * Opens an account with a zero balance.
	 *
	 * <p>The duplicate check exists to give a clear error message. It is not the guarantee — two
	 * simultaneous requests could both pass it before either commits. The unique constraint on
	 * the table is what actually makes duplicates impossible.
	 */
	@Transactional
	public AccountResponse openAccount(CreateAccountRequest request) {
		if (accountRepository.existsByAccountNumber(request.accountNumber())) {
			throw new AccountNumberAlreadyExistsException(request.accountNumber());
		}

		Account account = new Account(
				request.accountNumber(),
				request.ownerName(),
				request.currency());

		return AccountResponse.from(accountRepository.save(account));
	}

	/**
	 * Locks the account, adds money to it, and returns it.
	 *
	 * <p>Other features do not reach into account data themselves — they ask this service. The
	 * repository is package-private, so that is enforced by the compiler.
	 *
	 * <p>The lock is held until the caller's transaction commits, not until this method returns.
	 * A caller can therefore lock an account, write a ledger entry, and have both land together.
	 */
	@Transactional
	public Account creditAccount(Long accountId, BigDecimal amount) {
		Account account = accountRepository.findByIdForUpdate(accountId)
				.orElseThrow(() -> new AccountNotFoundException(accountId));

		account.credit(amount);
		return account;
	}

	/**
	 * Locks the account, takes money out of it, and returns it.
	 *
	 * <p>The lock is what makes the balance check trustworthy. Without it, two withdrawals could
	 * both read the same balance, both decide there is enough, and both succeed.
	 */
	@Transactional
	public Account debitAccount(Long accountId, BigDecimal amount) {
		Account account = accountRepository.findByIdForUpdate(accountId)
				.orElseThrow(() -> new AccountNotFoundException(accountId));

		account.debit(amount);
		return account;
	}

	/**
	 * {@code readOnly} lets Hibernate skip dirty-checking and lets the database optimise the
	 * transaction. It also documents that nothing here writes.
	 */
	@Transactional(readOnly = true)
	public List<AccountResponse> listAccounts() {
		return accountRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
				.stream()
				.map(AccountResponse::from)
				.toList();
	}
}
