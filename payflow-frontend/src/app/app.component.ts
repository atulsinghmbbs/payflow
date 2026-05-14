import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HeaderComponent } from '../components/header/header.component';
import { AlertComponent } from '../components/alert/alert.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, HeaderComponent, AlertComponent],
  template: `
    <app-header></app-header>
    <app-alert></app-alert>
    <main class="app-main">
      <router-outlet></router-outlet>
    </main>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100vh; }
    .app-main {
      flex: 1;
      padding: 28px 32px;
      background: #F7F8FC;
      max-width: 1200px;
      width: 100%;
      margin: 0 auto;
      box-sizing: border-box;
    }
    @media (max-width: 768px) {
      .app-main { padding: 20px 16px; }
    }
  `]
})
export class AppComponent {}