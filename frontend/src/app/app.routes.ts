import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/change-review/pages/new-review.page').then((m) => m.NewReviewPage)
  },
  {
    path: 'reviews',
    loadComponent: () =>
      import('./features/change-review/pages/history.page').then((m) => m.HistoryPage)
  },
  {
    path: 'reviews/:reviewId',
    loadComponent: () =>
      import('./features/change-review/pages/progress.page').then((m) => m.ProgressPage)
  },
  {
    path: 'reviews/:reviewId/clarification',
    loadComponent: () =>
      import('./features/change-review/pages/clarification.page').then((m) => m.ClarificationPage)
  },
  {
    path: 'reviews/:reviewId/report',
    loadComponent: () =>
      import('./features/change-review/pages/report.page').then((m) => m.ReportPage)
  },
  { path: '**', redirectTo: '' }
];
