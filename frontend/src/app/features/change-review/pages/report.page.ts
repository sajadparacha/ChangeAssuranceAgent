import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AffectedObjectView,
  ChangeAssuranceReport,
  CriticReview,
  DatabaseImpactSection,
  EvidenceReference,
  Finding,
  RiskAssessment,
  RiskScenario,
  idValue
} from '../models/change-review.models';
import { ChangeReviewFacade } from '../state/change-review.facade';

@Component({
  selector: 'app-report-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './report.page.html',
  styleUrl: './report.page.scss'
})
export class ReportPage {
  readonly facade = inject(ChangeReviewFacade);
  private readonly route = inject(ActivatedRoute);

  private static readonly severityOrder = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

  constructor() {
    const id = this.route.snapshot.paramMap.get('reviewId');
    if (id) {
      this.facade.loadReview(id);
      this.facade.loadReport(id);
    }
  }

  identification(report: ChangeAssuranceReport): Record<string, string> {
    return report.identification ?? {};
  }

  reportTitle(report: ChangeAssuranceReport): string {
    const id = this.identification(report);
    if (this.hasPackageCatalog(report) && id['packageName']) {
      return id['schemaOwner'] ? `${id['schemaOwner']}.${id['packageName']}` : id['packageName'];
    }
    return id['changeTitle'] || id['packageName'] || 'Change assurance report';
  }

  reportSubtitle(report: ChangeAssuranceReport): string {
    if (this.hasPackageCatalog(report)) {
      return 'Evidence-backed Oracle package impact review';
    }
    return 'Evidence-backed change assurance review';
  }

  hasPackageCatalog(report: ChangeAssuranceReport): boolean {
    const impact = report.databaseImpact;
    return !!(impact?.available && impact.packageFound);
  }

  affectedObjects(report: ChangeAssuranceReport): AffectedObjectView[] {
    const objects = report.agentUnderstanding?.affectedObjects;
    return Array.isArray(objects) ? objects : [];
  }

  objectLabel(obj: AffectedObjectView): string {
    return obj.schemaName ? `${obj.schemaName}.${obj.objectName}` : obj.objectName;
  }

  risk(report: ChangeAssuranceReport): RiskAssessment | null {
    const value = report.riskAssessment;
    if (!value || typeof value !== 'object' || !('riskLevel' in value)) {
      return null;
    }
    return value as RiskAssessment;
  }

  critic(report: ChangeAssuranceReport): CriticReview | null {
    const value = report.criticReview;
    if (!value || typeof value !== 'object') {
      return null;
    }
    return value as CriticReview;
  }

  scenarios(report: ChangeAssuranceReport): RiskScenario[] {
    return Array.isArray(report.riskScenarios) ? (report.riskScenarios as RiskScenario[]) : [];
  }

  orderedFindings(report: ChangeAssuranceReport): Array<{ severity: string; findings: Finding[] }> {
    const groups = report.findingsBySeverity ?? {};
    return ReportPage.severityOrder
      .filter((severity) => (groups[severity]?.length ?? 0) > 0)
      .map((severity) => ({ severity, findings: groups[severity] }));
  }

  sqlHighlight(
    report: ChangeAssuranceReport,
    finding: Finding
  ): { line: number | null; snippet: string } | null {
    const directSnippet = (finding.sqlSnippet || '').trim();
    if (directSnippet) {
      return {
        line: finding.sqlLine ?? null,
        snippet: directSnippet
      };
    }

    const evidence = this.evidenceForFinding(report, finding);
    const sql = evidence.find((e) => (e.evidenceType || '').toUpperCase() === 'SQL_STATEMENT')
      ?? evidence.find((e) => !!(e.extractedContent || '').trim() && e.lineNumber != null);
    const snippet = (sql?.extractedContent || '').trim();
    if (!snippet) {
      return null;
    }
    return {
      line: sql?.lineNumber ?? null,
      snippet
    };
  }

  private evidenceForFinding(report: ChangeAssuranceReport, finding: Finding): EvidenceReference[] {
    const refs = Array.isArray(report.evidenceReferences)
      ? (report.evidenceReferences as EvidenceReference[])
      : [];
    if (refs.length === 0 || !finding.evidenceIds?.length) {
      return [];
    }
    const wanted = new Set(
      finding.evidenceIds.map((id) => idValue(id)).filter((v): v is string => !!v)
    );
    return refs.filter((e) => wanted.has(idValue(e.evidenceId) || ''));
  }

  recommendationClass(value: string | undefined): string {
    switch ((value ?? '').toUpperCase()) {
      case 'GO':
        return 'rec-go';
      case 'CONDITIONAL_GO':
        return 'rec-conditional';
      case 'NO_GO':
        return 'rec-nogo';
      default:
        return 'rec-unknown';
    }
  }

  riskClass(level: string | undefined): string {
    switch ((level ?? '').toUpperCase()) {
      case 'CRITICAL':
      case 'HIGH':
        return 'risk-high';
      case 'MEDIUM':
        return 'risk-medium';
      case 'LOW':
        return 'risk-low';
      default:
        return 'risk-unknown';
    }
  }

  severityClass(severity: string): string {
    return `sev-${(severity || 'unknown').toLowerCase()}`;
  }

  catalogLabel(impact: DatabaseImpactSection): string {
    if (impact.catalogMode === 'oracle') {
      return 'Live Oracle catalog (read-only)';
    }
    if (impact.catalogMode === 'fake') {
      return 'Demo catalog';
    }
    return impact.catalogMode || 'Catalog';
  }

  impactClass(level: string | undefined): string {
    switch ((level ?? '').toUpperCase()) {
      case 'HIGH':
        return 'risk-high';
      case 'MEDIUM':
        return 'risk-medium';
      case 'LOW':
        return 'risk-low';
      default:
        return 'risk-unknown';
    }
  }

  htmlReportUrl(report: ChangeAssuranceReport): string {
    const id = this.identification(report)['reviewId'];
    return id ? `/api/v1/change-reviews/${id}/report.html` : '#';
  }

  scrollToSection(sectionId: string): void {
    const el = document.getElementById(sectionId);
    if (!el) {
      return;
    }
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  coveragePercent(score: number | undefined): string {
    if (score == null || Number.isNaN(score)) {
      return '—';
    }
    return `${Math.round(score * 100)}%`;
  }

  scenarioId(scenario: RiskScenario): string {
    return idValue(scenario.scenarioId) || scenario.title;
  }
}
