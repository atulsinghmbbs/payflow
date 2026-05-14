// // app.module.ts
// import { NgModule } from '@angular/core';
// import { BrowserModule } from '@angular/platform-browser';
// import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
// import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
// import { ReactiveFormsModule, FormsModule } from '@angular/forms';
// import { CommonModule } from '@angular/common';       // ← add this
// import { RouterModule } from '@angular/router';       // ← add this

// import { AppRoutingModule } from './app-routing.module';
// import { AppComponent } from './app.component';
// import { HttpErrorInterceptor } from '../interceptors/http-error.interceptor';

// // Components
// import { DashboardComponent } from '../components/dashboard/dashboard.component';
// import { TransactionFormComponent } from '../components/transaction-form/transaction-form.component';
// import { TransactionHistoryComponent } from '../components/transaction-history/transaction-history.component';
// import { HeaderComponent } from '../components/header/header.component';
// import { AlertComponent } from '../components/alert/alert.component';

// // Services
// import { PaymentService } from '../services/payment.service';
// import { NotificationService } from '../services/notification.service';

// @NgModule({
//   declarations: [
//     AppComponent,
//     DashboardComponent,
//     TransactionFormComponent,
//     TransactionHistoryComponent,
//     HeaderComponent,
//     AlertComponent,
//   ],
//   imports: [
//     BrowserModule,          // includes CommonModule for NgIf, NgFor, async pipe
//     BrowserAnimationsModule,
//     HttpClientModule,
//     ReactiveFormsModule,
//     FormsModule,
//     CommonModule,           // ← explicit, covers *ngIf, *ngFor, async, ngSwitch
//     RouterModule,           // ← covers routerLink, routerLinkActive
//     AppRoutingModule,
//   ],
//   providers: [
//     PaymentService,
//     NotificationService,
//     {
//       provide: HTTP_INTERCEPTORS,
//       useClass: HttpErrorInterceptor,
//       multi: true,
//     },
//   ],
//   bootstrap: [AppComponent],
// })
// export class AppModule {}