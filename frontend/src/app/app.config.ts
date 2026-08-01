import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { routes } from './app.routes';
import { correlationIdInterceptor } from './core/interceptors/correlation-id.interceptor';
import { ChangeReviewRepository } from './features/change-review/data-access/change-review.repository';
import { ChangeReviewHttpRepository } from './features/change-review/data-access/change-review-http.repository';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([correlationIdInterceptor])),
    provideAnimationsAsync(),
    { provide: ChangeReviewRepository, useExisting: ChangeReviewHttpRepository }
  ]
};
