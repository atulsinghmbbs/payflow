import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, transition, style, animate } from '@angular/animations';
import { NotificationService, Alert } from '../../services/notification.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-alert',
  standalone: true,
  imports: [CommonModule],
  animations: [
    trigger('slideIn', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateX(40px)' }),
        animate('220ms ease', style({ opacity: 1, transform: 'translateX(0)' }))
      ]),
      transition(':leave', [
        animate('180ms ease', style({ opacity: 0, transform: 'translateX(40px)' }))
      ])
    ])
  ],
  template: `
    <div class="alert-stack" aria-live="polite">
      <div
        *ngFor="let alert of alerts$ | async; trackBy: trackById"
        class="alert alert-{{ alert.type }}"
        [@slideIn]
      >
        <svg class="alert-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <ng-container [ngSwitch]="alert.type">
            <ng-container *ngSwitchCase="'success'">
              <path d="M22 11.08V12a10 10 0 11-5.93-9.14"/>
              <polyline points="22,4 12,14.01 9,11.01"/>
            </ng-container>
            <ng-container *ngSwitchCase="'error'">
              <circle cx="12" cy="12" r="10"/>
              <line x1="15" y1="9" x2="9" y2="15"/>
              <line x1="9" y1="9" x2="15" y2="15"/>
            </ng-container>
            <ng-container *ngSwitchCase="'warning'">
              <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/>
              <line x1="12" y1="17" x2="12.01" y2="17"/>
            </ng-container>
            <ng-container *ngSwitchDefault>
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </ng-container>
          </ng-container>
        </svg>
        <div class="alert-body">
          <div class="alert-title">{{ alert.title }}</div>
          <div class="alert-message">{{ alert.message }}</div>
          <div class="alert-code" *ngIf="alert.code">Error code: {{ alert.code }}</div>
        </div>
        <button class="dismiss-btn" (click)="notifications.dismiss(alert.id)" aria-label="Dismiss">✕</button>
      </div>
    </div>
  `,
  styles: [`
    .alert-stack {
      position: fixed; top: 80px; right: 24px;
      z-index: 9999; display: flex; flex-direction: column;
      gap: 10px; width: 380px; max-width: calc(100vw - 32px);
    }
.alert {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 14px 16px; border-radius: 12px;
  box-shadow: 0 8px 24px rgba(10,15,30,0.12); font-size: 13px;
}
.alert-success { background: #ECFAF3; border: 1px solid rgba(26,158,109,0.2); color: #1A9E6D; }
.alert-error   { background: #FEF0F0; border: 1px solid rgba(214,59,59,0.2);  color: #D63B3B; }
.alert-warning { background: #FFF6E0; border: 1px solid rgba(196,123,22,0.2); color: #C47B16; }
.alert-info    { background: #EBF4FF; border: 1px solid rgba(26,114,199,0.2); color: #1A72C7; }
    .alert-icon { width: 18px; height: 18px; flex-shrink: 0; margin-top: 1px; }
    .alert-body { flex: 1; }
    .alert-title   { font-weight: 700; margin-bottom: 2px; }
    .alert-message { font-size: 12px; opacity: 0.85; line-height: 1.5; }
    .alert-code    { font-family: monospace; font-size: 10px; margin-top: 4px; opacity: 0.6; }
    .dismiss-btn {
      background: none; border: none; cursor: pointer;
      color: inherit; opacity: 0.5; padding: 0 2px;
      font-size: 13px; line-height: 1; flex-shrink: 0; margin-top: 1px;
      &:hover { opacity: 1; }
    }
  `]
})
export class AlertComponent {
  alerts$: Observable<Alert[]>;
  constructor(public notifications: NotificationService) {
    this.alerts$ = notifications.alerts$;
  }
  trackById(_: number, alert: Alert): string { return alert.id; }
}