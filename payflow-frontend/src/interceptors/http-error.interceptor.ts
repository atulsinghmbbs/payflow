// http-error.interceptor.ts
import { Injectable } from '@angular/core';
import {
  HttpRequest, HttpHandler, HttpEvent, HttpInterceptor,
  HttpErrorResponse, HttpResponse
} from '@angular/common/http';
import { Observable, throwError, timer } from 'rxjs';
import { catchError, retryWhen, mergeMap, tap, finalize } from 'rxjs/operators';

@Injectable()
export class HttpErrorInterceptor implements HttpInterceptor {

  private readonly RETRYABLE_CODES = [409, 503]; // Conflict, Service Unavailable
  private readonly MAX_RETRIES = 3;

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const requestId = crypto.randomUUID();
    const startTime = Date.now();

    // Clone request with tracking header
    const trackedRequest = request.clone({
      setHeaders: { 'X-Request-ID': requestId }
    });

    return next.handle(trackedRequest).pipe(
      tap(event => {
        if (event instanceof HttpResponse) {
          const elapsed = Date.now() - startTime;
          console.debug(`[${requestId}] ${request.method} ${request.url} → ${event.status} (${elapsed}ms)`);
        }
      }),
      retryWhen(errors =>
        errors.pipe(
          mergeMap((error: HttpErrorResponse, retryIndex) => {
            // Retry only on retryable status codes and within limit
            if (retryIndex < this.MAX_RETRIES && this.RETRYABLE_CODES.includes(error.status)) {
              const delay = Math.pow(2, retryIndex) * 500; // Exponential backoff: 500ms, 1s, 2s
              console.warn(`[${requestId}] Retrying (${retryIndex + 1}/${this.MAX_RETRIES}) after ${delay}ms`);
              return timer(delay);
            }
            return throwError(() => error);
          })
        )
      ),
      catchError((error: HttpErrorResponse) => {
        console.error(`[${requestId}] Request failed:`, error.status, error.message);
        return throwError(() => error);
      }),
      finalize(() => {
        const elapsed = Date.now() - startTime;
        if (elapsed > 5000) {
          console.warn(`[${requestId}] Slow request: ${elapsed}ms for ${request.url}`);
        }
      })
    );
  }
}