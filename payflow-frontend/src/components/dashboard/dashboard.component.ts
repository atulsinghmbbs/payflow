import { Component, OnInit, OnDestroy, ChangeDetectorRef, NgZone, PLATFORM_ID, Inject } from '@angular/core';
import { isPlatformBrowser, CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { Router } from '@angular/router';
import { Subject, interval, takeUntil, switchMap, catchError, of } from 'rxjs';

import { PaymentService, AccountResponse, TransactionResponse } from '../../services/payment.service';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
})
export class DashboardComponent implements OnInit, OnDestroy {
  account: AccountResponse | null = null;
  recentTransactions: TransactionResponse[] = [];
  totalTransactions = 0;
  isLoading = true;
  isLoadingTxns = true;
  currentAccountId = 'ACC001';
  accounts: { id: string; label: string }[] = [];

  private destroy$ = new Subject<void>();
  private isBrowser: boolean;

  constructor(
    private paymentService: PaymentService,
    private notifications: NotificationService,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone,
    @Inject(PLATFORM_ID) platformId: Object
  ) {
    this.isBrowser = isPlatformBrowser(platformId);
  }

  ngOnInit(): void {
    // Only run API calls in the browser — skip SSR to avoid hydration mismatch
    if (!this.isBrowser) return;

    this.loadAccounts();
    this.loadDashboard();

    // Poll balance every 30 seconds
    interval(30_000).pipe(
      takeUntil(this.destroy$),
      switchMap(() =>
        this.paymentService.getAccountBalance(this.currentAccountId).pipe(
          catchError(() => of(null))
        )
      )
    ).subscribe(acc => {
      if (acc) {
        this.ngZone.run(() => {
          this.account = acc;
          this.cdr.detectChanges();
        });
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadAccounts(): void {
    this.paymentService.getAllAccounts().pipe(
      takeUntil(this.destroy$),
      catchError(() => {
        this.accounts = [
          { id: 'ACC001', label: 'Atul Sharma' },
          { id: 'ACC002', label: 'Priya Mehta' },
          { id: 'ACC003', label: 'Rahul Gupta' },
          { id: 'ACC004', label: 'Ananya Singh' },
          { id: 'ACC005', label: 'Vikram Patel' },
        ];
        this.cdr.detectChanges();
        return of([] as AccountResponse[]);
      })
    ).subscribe((list: AccountResponse[]) => {
      if (list.length > 0) {
        this.accounts = list.map(a => ({ id: a.id, label: a.ownerName }));
        this.cdr.detectChanges();
      }
    });
  }

  loadDashboard(): void {
    this.isLoading = true;
    this.isLoadingTxns = true;
    this.cdr.detectChanges();

    // ── Balance ──────────────────────────────────────────────────────────────
    this.paymentService.getAccountBalance(this.currentAccountId).pipe(
      takeUntil(this.destroy$),
      catchError(err => {
        console.error('Balance error:', err);
        this.notifications.fromApiError(err);
        this.ngZone.run(() => {
          this.isLoading = false;
          this.cdr.detectChanges();
        });
        return of(null);
      })
    ).subscribe(acc => {
      this.ngZone.run(() => {
        if (acc) this.account = acc;
        this.isLoading = false;
        this.cdr.detectChanges();
      });
    });

    // ── Transactions ─────────────────────────────────────────────────────────
    this.paymentService.getAccountTransactions(this.currentAccountId, 0, 10).pipe(
      takeUntil(this.destroy$),
      catchError(err => {
        console.error('Transactions error:', err);
        this.notifications.fromApiError(err);
        this.ngZone.run(() => {
          this.isLoadingTxns = false;
          this.cdr.detectChanges();
        });
        return of(null);
      })
    ).subscribe(page => {
      this.ngZone.run(() => {
        if (page) {
          this.recentTransactions = page.content;
          this.totalTransactions = page.totalElements;
        }
        this.isLoadingTxns = false;
        this.cdr.detectChanges();
      });
    });
  }

  onAccountChange(accountId: string): void {
    this.currentAccountId = accountId;
    this.account = null;
    this.recentTransactions = [];
    this.loadDashboard();
  }

  navigateToNew(): void { this.router.navigate(['/transactions/new']); }
  navigateToHistory(): void { this.router.navigate(['/transactions']); }

  reverseTransaction(id: string, event: Event): void {
    event.stopPropagation();
    this.paymentService.reverseTransaction(id).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.notifications.success('Reversed', 'Transaction successfully reversed');
        this.loadDashboard();
      },
      error: err => this.notifications.fromApiError(err),
    });
  }

  isDebit(type: string): boolean { return type === 'DEBIT' || type === 'TRANSFER'; }

  formatCurrency(amount: number, currency = 'USD'): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
  }

  get dailyUsagePercent(): number { return 0; }
}