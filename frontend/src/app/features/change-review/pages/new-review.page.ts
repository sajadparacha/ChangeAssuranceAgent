import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ChangeReviewFacade } from '../state/change-review.facade';

@Component({
  selector: 'app-new-review-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatCardModule
  ],
  templateUrl: './new-review.page.html',
  styleUrl: './new-review.page.scss'
})
export class NewReviewPage {
  private readonly fb = inject(FormBuilder);
  readonly facade = inject(ChangeReviewFacade);
  selectedFile: File | null = null;

  readonly form = this.fb.nonNullable.group({
    applicationName: ['', Validators.required],
    changeTitle: ['', Validators.required],
    changeDescription: [''],
    changeType: ['UNKNOWN'],
    targetEnvironment: [''],
    implementationWindow: [''],
    deploymentPlan: [''],
    rollbackPlan: [''],
    testEvidence: ['']
  });

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.facade.submit({ ...this.form.getRawValue(), sqlFile: this.selectedFile });
  }
}
