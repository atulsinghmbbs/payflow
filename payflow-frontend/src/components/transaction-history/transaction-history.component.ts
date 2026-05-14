import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { Subject, takeUntil, debounceTime, distinctUntilChanged } from 'rxjs';
import { FormControl } from '@angular/forms';

import { PaymentService, TransactionResponse, PagedResponse } from '../../services/payment.service';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-transaction-history',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './transaction-history.component.html',
  styleUrls: ['./transaction-history.component.scss'],
})
export class TransactionHistoryComponent implements OnInit, OnDestroy {
  Math = Math;
  transactions: TransactionResponse[] = [];
  page = 0; pageSize = 20; totalElements = 0; totalPages = 0;
  isLoading = false; selectedTxn: TransactionResponse | null = null;
  showModal = false; isReversing = false;

  accountIdControl = new FormControl('ACC001');
  statusFilter = new FormControl('');
  typeFilter = new FormControl('');

  readonly accountOptions = [
    { value: 'ACC001', label: 'Atul Sharma' },
    { value: 'ACC002', label: 'Priya Mehta' },
    { value: 'ACC003', label: 'Rahul Gupta' },
    { value: 'ACC004', label: 'Ananya Singh' },
    { value: 'ACC005', label: 'Vikram Patel' },
  ];
  readonly statusOptions = ['', 'COMPLETED', 'PENDING', 'PROCESSING', 'FAILED', 'REVERSED'];
  readonly typeOptions = ['', 'DEBIT', 'CREDIT', 'TRANSFER', 'REFUND'];

  private destroy$ = new Subject<void>();

  constructor(private paymentService: PaymentService, private notifications: NotificationService) {}

  ngOnInit(): void {
    this.loadTransactions();
    this.accountIdControl.valueChanges.pipe(debounceTime(200), distinctUntilChanged(), takeUntil(this.destroy$))
      .subscribe(() => { this.page = 0; this.loadTransactions(); });
  }

  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }

  loadTransactions(): void {
    const accountId = this.accountIdControl.value ?? 'ACC001';
    this.isLoading = true;
    this.paymentService.getAccountTransactions(accountId, this.page, this.pageSize)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data: PagedResponse<TransactionResponse>) => {
          this.transactions = data.content; this.totalElements = data.totalElements;
          this.totalPages = data.totalPages; this.isLoading = false;
        },
        error: () => { this.isLoading = false; this.transactions = []; this.totalElements = 0; },
      });
  }

  get filteredTransactions(): TransactionResponse[] {
    return this.transactions.filter(t => {
      const statusOk = !this.statusFilter.value || t.status === this.statusFilter.value;
      const typeOk = !this.typeFilter.value || t.type === this.typeFilter.value;
      return statusOk && typeOk;
    });
  }

  get pages(): number[] { return Array.from({ length: this.totalPages }, (_, i) => i); }

  goToPage(p: number): void { if (p < 0 || p >= this.totalPages) return; this.page = p; this.loadTransactions(); }
  openDetail(txn: TransactionResponse): void { this.selectedTxn = txn; this.showModal = true; }
  closeModal(): void { this.showModal = false; this.selectedTxn = null; this.isReversing = false; }

  reverseTransaction(): void {
    if (!this.selectedTxn) return;
    this.isReversing = true;
    this.paymentService.reverseTransaction(this.selectedTxn.id).pipe(takeUntil(this.destroy$)).subscribe({
      next: () => { this.notifications.success('Reversed', 'Transaction reversed successfully'); this.closeModal(); this.loadTransactions(); },
      error: err => { this.isReversing = false; this.notifications.fromApiError(err); },
    });
  }

  isDebit(type: string): boolean { return type === 'DEBIT' || type === 'TRANSFER'; }
  formatCurrency(amount: number, currency = 'USD'): string {
    return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(amount);
  }
  get startRecord(): number { return this.page * this.pageSize + 1; }
  get endRecord(): number { return Math.min((this.page + 1) * this.pageSize, this.totalElements); }
}