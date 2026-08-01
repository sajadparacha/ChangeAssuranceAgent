import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { ChangeReviewFacade } from '../state/change-review.facade';

@Component({
  selector: 'app-history-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatTableModule, RouterLink, MatButtonModule],
  templateUrl: './history.page.html',
  styleUrl: './history.page.scss'
})
export class HistoryPage {
  readonly facade = inject(ChangeReviewFacade);
  readonly columns = ['reviewId', 'applicationName', 'changeTitle', 'status', 'riskLevel', 'recommendation', 'actions'];

  constructor() {
    this.facade.loadHistory();
  }
}
