import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * One account, exactly as the backend sends it. Mirrors `AccountResponse` on the server.
 *
 * `balance` arrives as a JSON number. It is for display only — never do arithmetic on it here.
 * JavaScript numbers are floating point, which is the precision problem the backend avoids by
 * using BigDecimal. Money is calculated on the server; the browser only shows the result.
 */
export interface Account {
  id: number;
  accountNumber: string;
  ownerName: string;
  currency: string;
  balance: number;
  createdAt: string;
}

/** Talks to the accounts endpoints. Only this service knows the URL. */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);

  listAccounts(): Observable<Account[]> {
    return this.http.get<Account[]>('/api/accounts');
  }
}
