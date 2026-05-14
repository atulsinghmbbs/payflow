import { Routes } from '@angular/router';
import { DashboardComponent } from '../components/dashboard/dashboard.component';
import { TransactionFormComponent } from '../components/transaction-form/transaction-form.component';
import { TransactionHistoryComponent } from '../components/transaction-history/transaction-history.component';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'transactions/new', component: TransactionFormComponent },
  { path: 'transactions', component: TransactionHistoryComponent },
];