// header.component.ts
import { Component } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';

@Component({
  selector: 'app-header',
  template: `
    <header class="header">
      <div class="logo">
        <div class="logo-mark">
          <svg viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2.5">
            <rect x="2" y="5" width="20" height="14" rx="2"/>
            <path d="M2 10h20"/>
          </svg>
        </div>
        PayFlow
      </div>

      <nav class="nav">
        <a routerLink="/dashboard"         routerLinkActive="active">Dashboard</a>
        <a routerLink="/transactions/new"  routerLinkActive="active">New Transaction</a>
        <a routerLink="/transactions"      routerLinkActive="active">History</a>
      </nav>

      <div class="header-right">
        <div class="api-status">
          <span class="status-dot"></span>
          <span class="status-text">API Live</span>
        </div>
        <a href="http://localhost:8080/swagger-ui.html" target="_blank" class="docs-link">
          API Docs ↗
        </a>
      </div>
    </header>
  `,
  styles: [`
    .header {
      background: #0A0F1E;
      height: 64px;
      padding: 0 32px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      position: sticky;
      top: 0;
      z-index: 100;
    }

    .logo {
      display: flex;
      align-items: center;
      gap: 10px;
      color: white;
      font-size: 18px;
      font-weight: 700;
      letter-spacing: -0.3px;
    }

    .logo-mark {
      width: 32px;
      height: 32px;
      background: #3B5BDB;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      svg { width: 18px; height: 18px; }
    }

    .nav {
      display: flex;
      gap: 4px;

      a {
        color: rgba(255,255,255,0.55);
        text-decoration: none;
        font-size: 13px;
        font-weight: 500;
        padding: 6px 14px;
        border-radius: 8px;
        transition: all 0.15s;

        &:hover  { color: white; background: rgba(255,255,255,0.08); }
        &.active { color: white; background: rgba(59,91,219,0.4); }
      }
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 16px;
    }

    .api-status {
      display: flex;
      align-items: center;
      gap: 7px;
    }

    .status-dot {
      width: 8px;
      height: 8px;
      background: #22D3A3;
      border-radius: 50%;
      box-shadow: 0 0 0 3px rgba(34,211,163,0.25);
      animation: pulse 2s infinite;
    }

    @keyframes pulse {
      0%, 100% { box-shadow: 0 0 0 3px rgba(34,211,163,0.25); }
      50%       { box-shadow: 0 0 0 6px rgba(34,211,163,0.1); }
    }

    .status-text {
      font-size: 12px;
      color: rgba(255,255,255,0.5);
    }

    .docs-link {
      font-size: 12px;
      color: rgba(255,255,255,0.45);
      text-decoration: none;
      border: 1px solid rgba(255,255,255,0.12);
      padding: 5px 12px;
      border-radius: 6px;
      transition: all 0.15s;

      &:hover { color: white; border-color: rgba(255,255,255,0.3); }
    }

    @media (max-width: 768px) {
      .nav { display: none; }
      .docs-link { display: none; }
    }
  `]
})
export class HeaderComponent {}