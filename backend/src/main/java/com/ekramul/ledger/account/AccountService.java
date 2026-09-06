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
	 * Moves money from one account to another.
	 *
	 * <p><strong>Both accounts are locked, always in ascending id order.</strong> That ordering is
	 * what prevents deadlock. If transfer A&rarr;B locked A then B, while transfer B&rarr;A locked
	 * B then A, each would hold what the other needs and neither would ever finish. Locking the
	 * lower id first means both transfers queue for the same row, so one waits and the other runs.
	 *
	 * <p>The debit and the credit happen in one transaction. Either both land or neither does —
	 * money is never in flight between two accounts.
	 */
	@Transactional
	public AccountPair moveMoney(Long fromAccountId, Long toAccountId, BigDecimal amount) {
		if (fromAccountId.equals(toAccountId)) {
			throw new InvalidTransferException("Cannot transfer to the same account");
		}

		Long lowerId = Math.min(fromAccountId, toAccountId);
		Long higherId = Math.max(fromAccountId, toAccountId);

		Account lower = lockForUpdate(lowerId);
		Account higher = lockForUpdate(higherId);

		boolean sourceIsLower = fromAccountId.equals(lowerId);
		Account from = sourceIsLower ? lower : higher;
		Account to = sourceIsLower ? higher : lower;

		if (!from.getCurrency().equals(to.getCurrency())) {
			throw new InvalidTransferException("Cannot transfer between accounts in different currencies: "
					+ from.getCurrency() + " to " + to.getCurrency());
		}

		from.debit(amount);
		to.credit(amount);

		return new AccountPair(from, to);
	}

	private Account lockForUpdate(Long accountId) {
		return accountRepository.findByIdForUpdate(accountId)
				.orElseThrow(() -> new AccountNotFoundException(accountId));
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
