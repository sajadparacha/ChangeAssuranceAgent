import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { AiProviderOption } from '../models/change-review.models';
import { ChangeReviewFacade } from '../state/change-review.facade';

type ReviewMode = 'changePackage' | 'packageImpact';

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
export class NewReviewPage implements OnInit {
  private readonly fb = inject(FormBuilder);
  readonly facade = inject(ChangeReviewFacade);
  readonly reviewMode = signal<ReviewMode>('changePackage');
  readonly showOptionalPackage = signal(false);
  readonly formError = signal<string | null>(null);
  readonly selectedProviderId = signal('');
  selectedFile: File | null = null;

  readonly form = this.fb.nonNullable.group({
    packageName: [''],
    schemaOwner: [''],
    applicationName: [''],
    changeTitle: [''],
    changeDescription: [''],
    changeType: ['UNKNOWN'],
    targetEnvironment: [''],
    implementationWindow: [''],
    aiProvider: [''],
    aiModel: [''],
    customAiModel: ['']
  });

  readonly selectedProvider = computed(() => {
    const config = this.facade.aiConfig();
    const providerId = this.selectedProviderId();
    if (!config?.providers?.length) {
      return null;
    }
    return config.providers.find((p) => p.id === providerId) ?? config.providers[0] ?? null;
  });

  ngOnInit(): void {
    this.applyModeValidators('changePackage');
    this.facade.loadAiConfig((config) => {
      const providerId = config.defaultProvider
        || config.providers.find((p) => p.available)?.id
        || config.providers[0]?.id
        || '';
      const provider = config.providers.find((p) => p.id === providerId);
      const defaultId = provider?.models.find((m) => m.isDefault)?.id
        ?? provider?.defaultModel
        ?? config.defaultModel
        ?? '';
      this.selectedProviderId.set(providerId);
      this.form.patchValue({ aiProvider: providerId, aiModel: defaultId });
    });

    this.form.controls.aiProvider.valueChanges.subscribe((providerId) => {
      this.selectedProviderId.set(providerId);
      this.onProviderChanged(providerId);
    });
  }

  setReviewMode(mode: ReviewMode): void {
    this.reviewMode.set(mode);
    this.formError.set(null);
    this.applyModeValidators(mode);
    if (mode === 'packageImpact') {
      this.showOptionalPackage.set(false);
    }
  }

  toggleOptionalPackage(): void {
    this.showOptionalPackage.update((v) => !v);
  }

  onProviderChanged(providerId: string): void {
    const provider = this.findProvider(providerId);
    if (!provider) {
      return;
    }
    const defaultId = provider.models.find((m) => m.isDefault)?.id ?? provider.defaultModel ?? '';
    this.form.patchValue({ aiModel: defaultId, customAiModel: '' });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
    this.formError.set(null);
  }

  submit(): void {
    this.formError.set(null);
    this.applyModeValidators(this.reviewMode());
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.formError.set('Please complete the required fields for this review type.');
      return;
    }
    if (this.reviewMode() === 'changePackage' && !this.selectedFile) {
      this.formError.set('Attach a SQL / PL/SQL change script for change-package reviews.');
      return;
    }

    const raw = this.form.getRawValue();
    const selected = raw.aiModel === '__custom__'
      ? raw.customAiModel.trim()
      : raw.aiModel.trim();

    const includePackage = this.reviewMode() === 'packageImpact'
      || (this.reviewMode() === 'changePackage' && this.showOptionalPackage() && !!raw.packageName.trim());

    this.facade.submit({
      packageName: includePackage ? raw.packageName.trim() || undefined : undefined,
      schemaOwner: includePackage ? (raw.schemaOwner.trim() || undefined) : undefined,
      applicationName: this.reviewMode() === 'changePackage'
        ? (raw.applicationName.trim() || undefined)
        : (raw.applicationName.trim() || undefined),
      changeTitle: this.reviewMode() === 'changePackage'
        ? (raw.changeTitle.trim() || undefined)
        : (raw.changeTitle.trim() || undefined),
      changeDescription: raw.changeDescription,
      changeType: raw.changeType,
      targetEnvironment: raw.targetEnvironment,
      implementationWindow: raw.implementationWindow,
      aiProvider: raw.aiProvider.trim() || undefined,
      aiModel: selected || undefined,
      sqlFile: this.selectedFile
    });
  }

  private applyModeValidators(mode: ReviewMode): void {
    const pkg = this.form.controls.packageName;
    const app = this.form.controls.applicationName;
    const title = this.form.controls.changeTitle;
    if (mode === 'packageImpact') {
      pkg.setValidators([Validators.required]);
      app.clearValidators();
      title.clearValidators();
    } else {
      pkg.clearValidators();
      app.setValidators([Validators.required]);
      title.setValidators([Validators.required]);
    }
    pkg.updateValueAndValidity({ emitEvent: false });
    app.updateValueAndValidity({ emitEvent: false });
    title.updateValueAndValidity({ emitEvent: false });
  }

  private findProvider(providerId: string): AiProviderOption | null {
    const config = this.facade.aiConfig();
    if (!config?.providers?.length) {
      return null;
    }
    return config.providers.find((p) => p.id === providerId) ?? null;
  }
}
