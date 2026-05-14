// notification.service.ts
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export type AlertType = 'success' | 'error' | 'warning' | 'info';

export interface Alert {
  id: string;
  type: AlertType;
  title: string;
  message: string;
  code?: string;
  autoDismiss?: boolean;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private alertsSubject = new BehaviorSubject<Alert[]>([]);
  alerts$: Observable<Alert[]> = this.alertsSubject.asObservable();

  show(type: AlertType, title: string, message: string, code?: string, autoDismiss = true): void {
    const alert: Alert = {
      id: crypto.randomUUID(),
      type,
      title,
      message,
      code,
      autoDismiss,
    };

    this.alertsSubject.next([...this.alertsSubject.value, alert]);

    if (autoDismiss) {
      setTimeout(() => this.dismiss(alert.id), 6000);
    }
  }

  success(title: string, message: string): void {
    this.show('success', title, message);
  }

  error(title: string, message: string, code?: string): void {
    this.show('error', title, message, code, false); // errors stay until dismissed
  }

  warning(title: string, message: string): void {
    this.show('warning', title, message);
  }

  info(title: string, message: string): void {
    this.show('info', title, message);
  }

  dismiss(id: string): void {
    this.alertsSubject.next(this.alertsSubject.value.filter(a => a.id !== id));
  }

  dismissAll(): void {
    this.alertsSubject.next([]);
  }

  // Maps backend error codes to human-readable messages
  fromApiError(error: any): void {
    const errorCodeMap: Record<string, string> = {
      INSUFFICIENT_FUNDS: 'Not enough balance to complete this transaction.',
      ACCOUNT_NOT_FOUND: 'The specified account could not be found.',
      ACCOUNT_INACTIVE: 'This account is not active.',
      TRANSACTION_NOT_FOUND: 'Transaction not found.',
      DUPLICATE_TRANSACTION: 'This transaction was already submitted.',
      DAILY_LIMIT_EXCEEDED: 'Your daily transaction limit has been reached.',
      CONCURRENT_MODIFICATION: 'A concurrent update conflict occurred. Please retry.',
      RATE_LIMIT_EXCEEDED: 'Too many requests. Please wait a moment.',
      SELF_TRANSFER: 'You cannot transfer to the same account.',
      CURRENCY_MISMATCH: 'Cross-currency transfers are not supported.',
      VALIDATION_ERROR: 'Please check your input fields.',
      NETWORK_ERROR: 'Cannot reach the server. Check your connection.',
    };

    const code = error?.errorCode || error?.error?.code || 'UNKNOWN_ERROR';
    const message = errorCodeMap[code] || error?.error?.message || error?.userMessage || 'An unexpected error occurred.';

    this.error('Transaction Failed', message, code);
  }
}