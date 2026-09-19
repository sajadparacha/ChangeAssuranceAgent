import { Observable } from 'rxjs';
import {
  AiConfig,
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

export abstract class ChangeReviewRepository {
  abstract getAiConfig(): Observable<AiConfig>;
  abstract submitReview(request: SubmitChangeReviewRequest): Observable<SubmitChangeReviewResponse>;
  abstract getReview(reviewId: string): Observable<ChangeReview>;
  abstract getReviewPlan(reviewId: string): Observable<ReviewPlan>;
  abstract getActivities(reviewId: string): Observable<ToolActivity[]>;
  abstract getInformationGaps(reviewId: string): Observable<InformationGap[]>;
  abstract submitAnswer(reviewId: string, request: SubmitAnswerRequest): Observable<void>;
  abstract getReport(reviewId: string): Observable<ChangeAssuranceReport>;
  abstract listReviews(): Observable<ChangeReviewSummary[]>;
}
