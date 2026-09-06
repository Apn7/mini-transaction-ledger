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
import com.ekramul.ledger.account.dto.AccountResponse;
import com.ekramul.ledger.account.dto.CreateAccountRequest;
import com.ekramul.ledger.entry.dto.DepositRequest;
import com.ekramul.ledger.entry.dto.TransferRequest;

/**
 * Transfers in both directions at once. None of them may deadlock.
 *
 * <p>Ten transfers fire together: five A&rarr;B and five B&rarr;A. If each transfer locked its own
 * source account first, one thread would hold A while waiting for B and another would hold B while
 * waiting for A. Neither could finish, and PostgreSQL would kill one of them after
 * {@code deadlock_timeout} with SQLSTATE 40P01, which Spring surfaces as
 * {@code CannotAcquireLockException}.
 *
 * <p>{@code AccountService.moveMoney} avoids that by always locking the <strong>lower account id
 * first</strong>, whichever way the money is going. Both directions then queue for the same row,
 * so one waits and the other proceeds.
 *
 * <p>The amounts cancel out — five out and five back, same value — so a clean run ends with both
 * balances exactly where they started. That is the money-conservation check.
 *
 * <p>Not {@code @Transactional}, for the same reason as {@link ConcurrentWithdrawalTest}: a test
 * transaction would hold the rows the worker threads are competing for.
 */
@SpringBootTest
class ConcurrentTransferTest {

	private static final int TRANSFERS = 10;
	private static final BigDecimal OPENING = new BigDecimal("1000.00");
	private static final BigDecimal AMOUNT = new BigDecimal("10.00");

	@Autowired
	private AccountService accountService;

	@Autowired
	private PostingService postingService;

	@Test
	void oppositeTransfersDoNotDeadlock() throws InterruptedException {
		Long a = openAccountHolding("DLKA-" + System.currentTimeMillis());
		Long b = openAccountHolding("DLKB-" + System.currentTimeMillis());

		AtomicInteger completed = new AtomicInteger();
		Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

		CountDownLatch startTogether = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(TRANSFERS);

		try (ExecutorService pool = Executors.newFixedThreadPool(TRANSFERS)) {
			for (int i = 0; i < TRANSFERS; i++) {
				// Alternate the direction, so both orderings are in flight at the same moment.
				Long from = (i % 2 == 0) ? a : b;
				Long to = (i % 2 == 0) ? b : a;

				pool.execute(() -> {
					try {
						startTogether.await();

						postingService.transfer(new TransferRequest(
								UUID.randomUUID(), from, to, AMOUNT, "crossing transfer"));

						completed.incrementAndGet();
					} catch (Throwable failure) {
						// A deadlock arrives here as CannotAcquireLockException.
						failures.add(failure);
					} finally {
						finished.countDown();
					}
				});
			}

			startTogether.countDown();

			assertThat(finished.await(30, TimeUnit.SECONDS))
					.as("all %d transfers finished before the timeout", TRANSFERS)
					.isTrue();
		}

		assertThat(failures).as("deadlocks and other failures").isEmpty();
		assertThat(completed.get()).as("transfers completed").isEqualTo(TRANSFERS);

		// Five out and five back at the same amount: nothing was created or destroyed.
		assertThat(balanceOf(a)).as("balance of A").isEqualByComparingTo(OPENING);
		assertThat(balanceOf(b)).as("balance of B").isEqualByComparingTo(OPENING);
	}

	/** Opens an account and funds it, both committed before the threads start. */
	private Long openAccountHolding(String accountNumber) {
		AccountResponse account = accountService.openAccount(
				new CreateAccountRequest(accountNumber, "Deadlock Test", "BDT"));

		postingService.deposit(account.id(), new DepositRequest(
				UUID.randomUUID(), OPENING, "opening balance"));

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
