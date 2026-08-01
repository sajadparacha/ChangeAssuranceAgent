import { JsonPipe, KeyValuePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { ChangeReviewFacade } from '../state/change-review.facade';

@Component({
  selector: 'app-report-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, RouterLink, JsonPipe, KeyValuePipe],
  templateUrl: './report.page.html',
  styleUrl: './report.page.scss'
})
export class ReportPage {
  readonly facade = inject(ChangeReviewFacade);
  private readonly route = inject(ActivatedRoute);

  constructor() {
    const id = this.route.snapshot.paramMap.get('reviewId');
    if (id) {
      this.facade.loadReview(id);
      this.facade.loadReport(id);
    }
  }
}
