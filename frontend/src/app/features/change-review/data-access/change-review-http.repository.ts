import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/configuration/api.config';
import { ChangeReviewRepository } from './change-review.repository';
import {
  ChangeAssuranceReport,
  ChangeReview,
  ChangeReviewSummary,
  InformationGap,
  ReviewPlan,
  SubmitAnswerRequest,
  SubmitChangeReviewRequest,
  SubmitChangeReviewResponse,
  ToolActivity
} from '../models/change-review.models';

@Injectable({ providedIn: 'root' })
export class ChangeReviewHttpRepository extends ChangeReviewRepository {
  private readonly http = inject(HttpClient);
  private readonly base = API_BASE_URL;

  submitReview(request: SubmitChangeReviewRequest): Observable<SubmitChangeReviewResponse> {
    const form = new FormData();
    Object.entries(request).forEach(([key, value]) => {
      if (key === 'sqlFile') {
        if (value instanceof File) {
          form.append('sqlFile', value, value.name);
        }
        return;
      }
      if (value != null && value !== '') {
        form.append(key, String(value));
      }
    });
    return this.http.post<SubmitChangeReviewResponse>(`${this.base}/change-reviews`, form);
  }

  getReview(reviewId: string): Observable<ChangeReview> {
    return this.http.get<ChangeReview>(`${this.base}/change-reviews/${reviewId}`);
  }

  getReviewPlan(reviewId: string): Observable<ReviewPlan> {
    return this.http.get<ReviewPlan>(`${this.base}/change-reviews/${reviewId}/plan`);
  }

  getActivities(reviewId: string): Observable<ToolActivity[]> {
    return this.http.get<ToolActivity[]>(`${this.base}/change-reviews/${reviewId}/activities`);
  }

  getInformationGaps(reviewId: string): Observable<InformationGap[]> {
    return this.http.get<InformationGap[]>(`${this.base}/change-reviews/${reviewId}/information-gaps`);
  }

  submitAnswer(reviewId: string, request: SubmitAnswerRequest): Observable<void> {
    return this.http.post<void>(`${this.base}/change-reviews/${reviewId}/answers`, request);
  }

  getReport(reviewId: string): Observable<ChangeAssuranceReport> {
    return this.http.get<ChangeAssuranceReport>(`${this.base}/change-reviews/${reviewId}/report`);
  }

  listReviews(): Observable<ChangeReviewSummary[]> {
    return this.http.get<ChangeReviewSummary[]>(`${this.base}/change-reviews`);
  }
}
