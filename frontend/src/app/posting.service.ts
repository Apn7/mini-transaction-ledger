import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** One line of a statement. Mirrors `EntryResponse` on the server. */
export interface Entry {
  entryId: number;
  reference: string;
  accountId: number;
  direction: 'DEBIT' | 'CREDIT';
  amount: number;
  balanceAfter: number;
  description: string | null;
  createdAt: string;
}

/**
 * A fresh v4 UUID for a posting's idempotency key.
 *
 * `crypto.randomUUID()` is the one-line answer, but it only exists in a *secure context* — HTTPS
 * or localhost. Opened over a plain LAN address, say http://192.168.1.5:4200, it is `undefined`
 * and every posting would fail. `crypto.getRandomValues` carries no such restriction, so it backs
 * the fallback.
 */
export function newReference(): string {
  if (crypto.randomUUID) {
    return crypto.randomUUID();
  }

  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variant 1
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');

  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * Records money movements.
 *
 * <p>Every posting carries a `reference` — the idempotency key the backend stores under a unique
 * constraint. A double-clicked button sends the same reference twice and the second one is
 * rejected by that constraint instead of creating a second deposit.
 */
@Injectable({ providedIn: 'root' })
export class PostingService {
  private readonly http = inject(HttpClient);

  deposit(accountId: number, amount: string, description: string): Observable<Entry> {
    return this.http.post<Entry>(`/api/accounts/${accountId}/deposits`, {
      reference: newReference(),
      amount,
      description,
    });
  }

  withdraw(accountId: number, amount: string, description: string): Observable<Entry> {
    return this.http.post<Entry>(`/api/accounts/${accountId}/withdrawals`, {
      reference: newReference(),
      amount,
      description,
    });
  }

  transfer(
    fromAccountId: number,
    toAccountId: number,
    amount: string,
    description: string,
  ): Observable<Entry[]> {
    return this.http.post<Entry[]>('/api/transfers', {
      reference: newReference(),
      fromAccountId,
      toAccountId,
      amount,
      description,
    });
  }

  /** The account's statement, oldest first. Each line carries the balance it left behind. */
  statement(accountId: number): Observable<Entry[]> {
    return this.http.get<Entry[]>(`/api/accounts/${accountId}/entries`);
  }
}
