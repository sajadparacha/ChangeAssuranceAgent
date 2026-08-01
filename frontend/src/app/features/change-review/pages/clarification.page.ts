import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ChangeReviewFacade } from '../state/change-review.facade';
import { idValue } from '../models/change-review.models';

@Component({
  selector: 'app-clarification-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './clarification.page.html',
  styleUrl: './clarification.page.scss'
})
export class ClarificationPage {
  readonly facade = inject(ChangeReviewFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    answer: ['', Validators.required]
  });

  constructor() {
    const id = this.route.snapshot.paramMap.get('reviewId');
    if (id) {
      this.facade.loadReview(id);
    }
  }

  submit(): void {
    const review = this.facade.activeReview();
    const gap = this.facade.gaps()[0];
    if (!review || !gap || this.form.invalid) {
      return;
    }
    this.facade.submitAnswer(review.reviewId, {
      gapId: idValue(gap.gapId),
      answer: this.form.controls.answer.value
    });
  }
}
