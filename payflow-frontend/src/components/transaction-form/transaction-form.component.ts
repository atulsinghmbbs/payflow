import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';
import { v4 as uuidv4 } from 'uuid';

import { PaymentService, TransactionRequest } from '../../services/payment.service';
import { NotificationService } from '../../services/notification.service';

function positiveAmountValidator(control: AbstractControl): ValidationErrors | null {
  const val = parseFloat(control.value);
  if (isNaN(val) || val <= 0) return { invalidAmount: 'Amount must be positive' };
  if (val > 1_000_000) return { invalidAmount: 'Amount exceeds $1,000,000 limit' };
  return null;
}

function selfTransferValidator(group: AbstractControl): ValidationErrors | null {
  const type = group.get('type')?.value;
  const from = group.get('accountId')?.value;
  const to = group.get('toAccountId')?.value;
  if (type === 'TRANSFER' && from && to && from === to)
    return { selfTransfer: 'Source and destination accounts must differ' };
  return null;
}

function toAccountRequiredForTransfer(group: AbstractControl): ValidationErrors | null {
  const type = group.get('type')?.value;
  const to = group.get('toAccountId')?.value;
  if (type === 'TRANSFER' && (!to || to.trim() === ''))
    return { toAccountRequired: 'Destination account is required for transfers' };
  return null;
}

@Component({
  selector: 'app-transaction-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './transaction-form.component.html',
  styleUrls: ['./transaction-form.component.scss'],
})
export class TransactionFormComponent implements OnInit, OnDestroy {
  form!: FormGroup;
  isSubmitting = false;
  idempotencyKey = '';
  private destroy$ = new Subject<void>();

  readonly transactionTypes = ['DEBIT', 'CREDIT', 'TRANSFER', 'REFUND'] as const;
  readonly currencies = ['USD', 'EUR', 'GBP', 'INR', 'AED', 'SGD'];
  readonly accounts = [
    { id: 'ACC001', label: 'Atul Sharma — ACC-0001', balance: 100000 },
    { id: 'ACC002', label: 'Priya Mehta — ACC-0002', balance: 50000 },
    { id: 'ACC003', label: 'Rahul Gupta — ACC-0003', balance: 75000 },
    { id: 'ACC004', label: 'Ananya Singh — ACC-0004', balance: 25000 },
    { id: 'ACC005', label: 'Vikram Patel — ACC-0005', balance: 200000 },
  ];

  constructor(
    private fb: FormBuilder,
    private paymentService: PaymentService,
    private notifications: NotificationService,
    private router: Router
  ) {}

  ngOnInit(): void { this.generateIdempotencyKey(); this.buildForm(); this.watchTypeChanges(); }
  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }

  private buildForm(): void {
    this.form = this.fb.group({
      accountId:   ['ACC001', Validators.required],
      toAccountId: [''],
      amount:      [null, [Validators.required, positiveAmountValidator]],
      currency:    ['USD', [Validators.required, Validators.pattern(/^[A-Z]{3}$/)]],
      type:        ['DEBIT', Validators.required],
      description: ['', Validators.maxLength(255)],
    }, { validators: [selfTransferValidator, toAccountRequiredForTransfer] });
  }

  private watchTypeChanges(): void {
    this.form.get('type')!.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(type => {
      const ctrl = this.form.get('toAccountId')!;
      if (type === 'TRANSFER') { ctrl.setValidators([Validators.required]); }
      else { ctrl.clearValidators(); ctrl.setValue(''); }
      ctrl.updateValueAndValidity();
    });
  }

  generateIdempotencyKey(): void { this.idempotencyKey = uuidv4(); }
  get isTransfer(): boolean { return this.form?.get('type')?.value === 'TRANSFER'; }

  fieldError(field: string): string | null {
    const control = this.form.get(field);
    if (!control || !control.touched || !control.errors) return null;
    const e = control.errors;
    if (e['required']) return 'This field is required';
    if (e['maxlength']) return `Max ${e['maxlength'].requiredLength} characters`;
    if (e['pattern']) return 'Invalid format';
    if (e['invalidAmount']) return e['invalidAmount'];
    return 'Invalid value';
  }

  formError(key: string): string | null { return this.form.errors?.[key] || null; }

  onSubmit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.notifications.warning('Validation Error', 'Please correct the highlighted fields.'); return; }
    this.isSubmitting = true;
    const payload: TransactionRequest = { ...this.form.value, toAccountId: this.isTransfer ? this.form.value.toAccountId : undefined };
    this.paymentService.initiateTransaction(payload).pipe(takeUntil(this.destroy$)).subscribe({
      next: response => {
        this.isSubmitting = false;
        this.notifications.success('✓ Transaction Successful', `${response.referenceId} — $${response.amount} ${response.type.toLowerCase()} completed`);
        this.generateIdempotencyKey();
        this.router.navigate(['/dashboard']);
      },
      error: err => { this.isSubmitting = false; this.generateIdempotencyKey(); this.notifications.fromApiError(err); },
    });
  }

  onReset(): void {
    this.form.reset({ accountId: 'ACC001', currency: 'USD', type: 'DEBIT' });
    this.generateIdempotencyKey();
    this.notifications.dismissAll();
  }
}