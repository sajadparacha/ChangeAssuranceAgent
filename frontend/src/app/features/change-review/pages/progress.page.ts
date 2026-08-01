import { JsonPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ChangeReviewFacade } from '../state/change-review.facade';

@Component({
  selector: 'app-progress-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatCardModule, MatProgressBarModule, RouterLink, JsonPipe],
  templateUrl: './progress.page.html',
  styleUrl: './progress.page.scss'
})
export class ProgressPage implements OnDestroy {
  readonly facade = inject(ChangeReviewFacade);
  private readonly route = inject(ActivatedRoute);

  constructor() {
    const id = this.route.snapshot.paramMap.get('reviewId');
    if (id) {
      this.facade.loadReview(id);
    }
  }

  ngOnDestroy(): void {
    this.facade.stopPolling();
  }
}
