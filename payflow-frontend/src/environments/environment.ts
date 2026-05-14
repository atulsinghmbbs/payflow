// environment.ts — Development
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8081/api/v1/payments',
  pollingIntervalMs: 30_000,
  maxRetries: 3,
  retryDelayMs: 500,
};

