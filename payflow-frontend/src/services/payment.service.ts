// payment.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, throwError, timer } from 'rxjs';
import { catchError, retry, retryWhen, delayWhen, tap } from 'rxjs/operators';
import { v4 as uuidv4 } from 'uuid';

export interface TransactionRequest {
  accountId: string;
  toAccountId?: string;
  amount: number;
  currency: string;
  type: 'DEBIT' | 'CREDIT' | 'TRANSFER' | 'REFUND';
  description?: string;
}

export interface TransactionResponse {
  id: string;
  referenceId: string;
  accountId: string;
  toAccountId?: string;
  amount: number;
  currency: string;
  type: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'REVERSED' | 'DUPLICATE';
  description?: string;
  failureReason?: string;
  createdAt: string;
  processedAt?: string;
  version: number;
}

export interface AccountResponse {
  id: string;
  accountNumber: string;
  ownerName: string;
  balance: number;
  availableBalance: number;
  currency: string;
  status: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface ErrorResponse {
  errorId: string;
  code: string;
  message: string;
  path: string;
  timestamp: string;
  fieldErrors?: Record<string, string>;
}

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly baseUrl = 'http://localhost:8080/api/v1/payments';

  constructor(private http: HttpClient) {}

  // ─── Initiate Transaction (with idempotency key) ──────────────────────────
  initiateTransaction(request: TransactionRequest): Observable<TransactionResponse> {
    const idempotencyKey = uuidv4(); // Generate unique key per attempt
    const headers = new HttpHeaders({
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    });

    return this.http
      .post<TransactionResponse>(`${this.baseUrl}/transactions`, request, { headers })
      .pipe(
        tap(res => console.log('Transaction created:', res.referenceId)),
        catchError(this.handleError)
      );
  }

  // ─── Get Transaction by ID ─────────────────────────────────────────────────
  getTransaction(id: string): Observable<TransactionResponse> {
    return this.http
      .get<TransactionResponse>(`${this.baseUrl}/transactions/${id}`)
      .pipe(catchError(this.handleError));
  }

  // ─── Get Account Transactions ──────────────────────────────────────────────
  getAccountTransactions(
    accountId: string,
    page = 0,
    size = 20
  ): Observable<PagedResponse<TransactionResponse>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sortBy', 'createdAt')
      .set('sortDir', 'desc');

    return this.http
      .get<PagedResponse<TransactionResponse>>(
        `${this.baseUrl}/accounts/${accountId}/transactions`,
        { params }
      )
      .pipe(catchError(this.handleError));
  }

  // ─── Get Account Balance ───────────────────────────────────────────────────
  getAccountBalance(accountId: string): Observable<AccountResponse> {
    return this.http
      .get<AccountResponse>(`${this.baseUrl}/accounts/${accountId}/balance`)
      .pipe(catchError(this.handleError));
  }

  getAllAccounts(): Observable<AccountResponse[]> {
    return this.http
      .get<AccountResponse[]>(`${this.baseUrl}/accounts`)
      .pipe(catchError(this.handleError));
  }

  // ─── Reverse Transaction ───────────────────────────────────────────────────
  reverseTransaction(transactionId: string): Observable<TransactionResponse> {
    return this.http
      .post<TransactionResponse>(`${this.baseUrl}/transactions/${transactionId}/reverse`, {})
      .pipe(catchError(this.handleError));
  }

  // ─── Centralized Error Handler ─────────────────────────────────────────────
  private handleError(error: any): Observable<never> {
    let errorMessage = 'An unexpected error occurred';
    let errorCode = 'UNKNOWN_ERROR';

    if (error.status === 0) {
      errorMessage = 'Cannot connect to server. Please check your connection.';
      errorCode = 'NETWORK_ERROR';
    } else if (error.error) {
      const apiError = error.error as ErrorResponse;
      errorMessage = apiError.message || errorMessage;
      errorCode = apiError.code || errorCode;
    }

    const enrichedError = { ...error, userMessage: errorMessage, errorCode };
    console.error(`[${errorCode}] ${errorMessage}`, error);
    return throwError(() => enrichedError);
  }
}