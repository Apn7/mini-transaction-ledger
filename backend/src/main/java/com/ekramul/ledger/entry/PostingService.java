package com.ekramul.ledger.entry;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ekramul.ledger.account.Account;
import com.ekramul.ledger.account.AccountPair;
import com.ekramul.ledger.account.AccountService;
import com.ekramul.ledger.entry.dto.DepositRequest;
import com.ekramul.ledger.entry.dto.EntryResponse;
import com.ekramul.ledger.entry.dto.TransferRequest;
import com.ekramul.ledger.entry.dto.WithdrawalRequest;

/** Records money movements. Every method here is one all-or-nothing unit of work. */
@Service
public class PostingService {

	private final LedgerTransactionRepository transactionRepository;
	private final LedgerEntryRepository entryRepository;
	private final AccountService accountService;

	public PostingService(LedgerTransactionRepository transactionRepository,
			LedgerEntryRepository entryRepository,
			AccountService accountService) {
		this.transactionRepository = transactionRepository;
		this.entryRepository = entryRepository;
		this.accountService = accountService;
	}

	/**
	 * Puts money into an account.
	 *
	 * <p>Three things happen, and either all of them are saved or none are: the event is
	 * recorded, the balance goes up, and the statement line is written.
	 *
	 * <p>Nothing here catches exceptions. If any step fails the whole thing is rolled back, which
	 * is only possible because the failure is allowed to escape this method.
	 */
	@Transactional
	public EntryResponse deposit(Long accountId, DepositRequest request) {
		LedgerTransaction transaction = transactionRepository.save(new LedgerTransaction(
				request.reference(), TransactionType.DEPOSIT, request.description()));

		Account account = accountService.creditAccount(accountId, request.amount());

		LedgerEntry entry = entryRepository.save(new LedgerEntry(
				transaction, account, Direction.CREDIT, request.amount(), account.getBalance()));

		return EntryResponse.from(entry);
	}

	/**
	 * Takes money out of an account.
	 *
	 * <p>Mirror of {@link #deposit}, with one extra rule: the account must have the money. If it
	 * does not, {@code debitAccount} throws, this method does not catch it, and the transaction
	 * rolls back — so the event row written a moment ago is discarded too.
	 */
	@Transactional
	public EntryResponse withdraw(Long accountId, WithdrawalRequest request) {
		LedgerTransaction transaction = transactionRepository.save(new LedgerTransaction(
				request.reference(), TransactionType.WITHDRAWAL, request.description()));

		Account account = accountService.debitAccount(accountId, request.amount());

		LedgerEntry entry = entryRepository.save(new LedgerEntry(
				transaction, account, Direction.DEBIT, request.amount(), account.getBalance()));

		return EntryResponse.from(entry);
	}

	/**
	 * Moves money between two accounts.
	 *
	 * <p>One event, two entries: a DEBIT on the source and a CREDIT on the destination. They net
	 * to zero, which is what makes this double-entry rather than two unrelated movements.
	 *
	 * <p>All of it is one transaction. There is no moment where the money has left one account
	 * and not yet arrived at the other.
	 *
	 * @return the debit entry first, then the credit entry
	 */
	@Transactional
	public List<EntryResponse> transfer(TransferRequest request) {
		LedgerTransaction transaction = transactionRepository.save(new LedgerTransaction(
				request.reference(), TransactionType.TRANSFER, request.description()));

		AccountPair pair = accountService.moveMoney(
				request.fromAccountId(), request.toAccountId(), request.amount());

		LedgerEntry debit = entryRepository.save(new LedgerEntry(
				transaction, pair.from(), Direction.DEBIT, request.amount(), pair.from().getBalance()));

		LedgerEntry credit = entryRepository.save(new LedgerEntry(
				transaction, pair.to(), Direction.CREDIT, request.amount(), pair.to().getBalance()));

		return List.of(EntryResponse.from(debit), EntryResponse.from(credit));
	}

	/**
	 * Every entry for one account, oldest first.
	 *
	 * <p>The existence check is what separates "this account has posted nothing" (an empty list)
	 * from "there is no such account" (404). Without it the client cannot tell them apart.
	 */
	@Transactional(readOnly = true)
	public List<EntryResponse> statementFor(Long accountId) {
		accountService.requireExists(accountId);

		return entryRepository.findByAccountIdOrderByIdAsc(accountId)
				.stream()
				.map(EntryResponse::from)
				.toList();
	}
}
