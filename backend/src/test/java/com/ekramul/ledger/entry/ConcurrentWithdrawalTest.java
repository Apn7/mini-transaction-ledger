package com.ekramul.ledger.entry;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ekramul.ledger.account.AccountService;
import com.ekramul.ledger.account.InsufficientBalanceException;
import com.ekramul.ledger.account.dto.AccountResponse;
import com.ekramul.ledger.account.dto.CreateAccountRequest;
import com.ekramul.ledger.entry.dto.DepositRequest;
import com.ekramul.ledger.entry.dto.WithdrawalRequest;

/**
 * Ten threads try to withdraw the same money at the same moment. Exactly one may win.
 *
 * <p>This is the test the pessimistic lock exists for. Without
 * {@code @Lock(PESSIMISTIC_WRITE)} on {@code findByIdForUpdate}, all ten threads read a balance
 * of 100, all ten decide there is enough, and all ten succeed against a single 100.
 *
 * <p><strong>Deliberately not {@code @Transactional}.</strong> A test transaction would hold the
 * account row itself, and the worker threads would queue behind a lock that only releases when
 * the test ends. Concurrency has to be observed against committed data, so this test writes for
 * real and leaves its rows behind.
 *
 * <p>Lives in {@code com.ekramul.ledger.entry} so it can reach the package-private repositories
 * without widening their visibility for a test's convenience.
 */
@SpringBootTest
class ConcurrentWithdrawalTest {

	private static final int THREADS = 10;
	private static final BigDecimal BALANCE = new BigDecimal("100.00");

	@Autowired
	private AccountService accountService;

	@Autowired
	private PostingService postingService;

	@Autowired
	private LedgerEntryRepository entryRepository;

	@Autowired
	private LedgerTransactionRepository transactionRepository;

	@Test
	void onlyOneOfTenSimultaneousWithdrawalsSucceeds() throws InterruptedException {
		Long accountId = openAccountHolding(BALANCE);
		long transactionsBefore = transactionRepository.count();

		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger refused = new AtomicInteger();
		Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

		// Every thread waits on this one latch, so all ten fire together instead of trickling
		// in. Staggered threads would not overlap, and the race would never be exercised.
		CountDownLatch startTogether = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(THREADS);

		try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
			for (int i = 0; i < THREADS; i++) {
				pool.execute(() -> {
					try {
						startTogether.await();

						// A fresh reference per thread. Reusing one would have the unique
						// constraint reject the duplicates, and this would be testing
						// idempotency instead of locking.
						postingService.withdraw(accountId, new WithdrawalRequest(
								UUID.randomUUID(), BALANCE, "concurrent withdrawal"));

						succeeded.incrementAndGet();
					} catch (InsufficientBalanceException expected) {
						refused.incrementAndGet();
					} catch (Throwable other) {
						unexpected.add(other);
					} finally {
						finished.countDown();
					}
				});
			}

			startTogether.countDown();

			assertThat(finished.await(30, TimeUnit.SECONDS))
					.as("all %d threads finished before the timeout", THREADS)
					.isTrue();
		}

		assertThat(unexpected).as("failures that were neither success nor insufficient balance").isEmpty();
		assertThat(succeeded.get()).as("withdrawals that succeeded").isEqualTo(1);
		assertThat(refused.get()).as("withdrawals refused for insufficient balance").isEqualTo(THREADS - 1);

		// isEqualByComparingTo, not isEqualTo: 0 and 0.0000 are equal in value but differ in
		// scale, and BigDecimal.equals compares both.
		assertThat(balanceOf(accountId)).as("final balance").isEqualByComparingTo("0");

		// The nine losers each wrote a transactions row before being refused. If any of those
		// survived, the rollback is broken and the ledger holds withdrawals that never happened.
		assertThat(transactionRepository.count() - transactionsBefore)
				.as("transaction rows committed by %d attempts", THREADS)
				.isEqualTo(1);

		assertThat(entryRepository.findByAccountIdOrderByIdAsc(accountId))
				.as("statement lines: the opening deposit and exactly one withdrawal")
				.hasSize(2);
	}

	/** Opens a fresh account and funds it, both committed before the threads start. */
	private Long openAccountHolding(BigDecimal amount) {
		AccountResponse account = accountService.openAccount(new CreateAccountRequest(
				"CONC-" + System.currentTimeMillis(), "Concurrency Test", "BDT"));

		postingService.deposit(account.id(), new DepositRequest(
				UUID.randomUUID(), amount, "opening balance"));

		return account.id();
	}

	private BigDecimal balanceOf(Long accountId) {
		return accountService.listAccounts().stream()
				.filter(account -> account.id().equals(accountId))
				.map(AccountResponse::balance)
				.findFirst()
				.orElseThrow();
	}
}
