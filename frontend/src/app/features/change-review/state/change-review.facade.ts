import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription, interval, switchMap, takeWhile } from 'rxjs';
import { ChangeReviewHttpRepository } from '../data-access/change-review-http.repository';
import {
  AiConfig,
  ChangeAssuranceReport,
  ChangeReview,
  ChangeReviewSummary,
  InformationGap,
  ReviewPlan,
  SubmitAnswerRequest,
  SubmitChangeReviewRequest,
  ToolActivity
} from '../models/change-review.models';

@Injectable({ providedIn: 'root' })
export class ChangeReviewFacade {
  private readonly repository = inject(ChangeReviewHttpRepository);
  private readonly router = inject(Router);
  private pollSub?: Subscription;

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly aiConfig = signal<AiConfig | null>(null);
  readonly activeReview = signal<ChangeReview | null>(null);
  readonly plan = signal<ReviewPlan | null>(null);
  readonly activities = signal<ToolActivity[]>([]);
  readonly gaps = signal<InformationGap[]>([]);
  readonly report = signal<ChangeAssuranceReport | null>(null);
  readonly history = signal<ChangeReviewSummary[]>([]);

  readonly isWaitingForInformation = computed(
    () => this.activeReview()?.currentStage === 'WAITING_FOR_INFORMATION'
  );
  readonly isCompleted = computed(
    () => this.activeReview()?.status === 'COMPLETED' || this.activeReview()?.currentStage === 'COMPLETED'
  );

  loadAiConfig(onLoaded?: (config: AiConfig) => void): void {
    this.repository.getAiConfig().subscribe({
      next: (config) => {
        this.aiConfig.set(config);
        onLoaded?.(config);
      },
      error: () => this.aiConfig.set(null)
    });
  }

  submit(request: SubmitChangeReviewRequest): void {
    this.loading.set(true);
    this.error.set(null);
    this.repository.submitReview(request).subscribe({
      next: (res) => {
        this.loading.set(false);
        void this.router.navigate(['/reviews', res.reviewId]);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Submission failed');
      }
    });
  }

  loadReview(reviewId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.repository.getReview(reviewId).subscribe({
      next: (review) => {
        this.activeReview.set(review);
        this.loading.set(false);
        this.loadSideData(reviewId);
        if (review.currentStage === 'WAITING_FOR_INFORMATION') {
          void this.router.navigate(['/reviews', reviewId, 'clarification']);
        } else if (review.status === 'COMPLETED' || review.currentStage === 'COMPLETED') {
          this.loadReport(reviewId);
        } else {
          this.startPolling(reviewId);
        }
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to load review');
      }
    });
  }

  loadHistory(): void {
    this.repository.listReviews().subscribe({
      next: (items) => this.history.set(items),
      error: (err) => this.error.set(err?.error?.message ?? 'Failed to load history')
    });
  }

  loadReport(reviewId: string): void {
    this.repository.getReport(reviewId).subscribe({
      next: (report) => this.report.set(report),
      error: (err) => this.error.set(err?.error?.message ?? 'Failed to load report')
    });
  }

  submitAnswer(reviewId: string, request: SubmitAnswerRequest): void {
    this.loading.set(true);
    this.repository.submitAnswer(reviewId, request).subscribe({
      next: () => {
        this.loading.set(false);
        this.stopPolling();
        this.loadReview(reviewId);
        void this.router.navigate(['/reviews', reviewId]);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Failed to submit answer');
      }
    });
  }

  stopPolling(): void {
    this.pollSub?.unsubscribe();
    this.pollSub = undefined;
  }

  private loadSideData(reviewId: string): void {
    this.repository.getReviewPlan(reviewId).subscribe({ next: (p) => this.plan.set(p) });
    this.repository.getActivities(reviewId).subscribe({ next: (a) => this.activities.set(a) });
    this.repository.getInformationGaps(reviewId).subscribe({ next: (g) => this.gaps.set(g) });
  }

  private startPolling(reviewId: string): void {
    this.stopPolling();
    this.pollSub = interval(2000)
      .pipe(
        switchMap(() => this.repository.getReview(reviewId)),
        takeWhile(
          (review) =>
            review.currentStage !== 'COMPLETED'
            && review.currentStage !== 'FAILED'
            && review.currentStage !== 'WAITING_FOR_INFORMATION',
          true
        )
      )
      .subscribe({
        next: (review) => {
          this.activeReview.set(review);
          this.loadSideData(reviewId);
          if (review.currentStage === 'WAITING_FOR_INFORMATION') {
            this.stopPolling();
            void this.router.navigate(['/reviews', reviewId, 'clarification']);
          }
          if (review.currentStage === 'COMPLETED' || review.status === 'COMPLETED') {
            this.stopPolling();
            this.loadReport(reviewId);
            void this.router.navigate(['/reviews', reviewId, 'report']);
          }
        }
      });
  }
}
