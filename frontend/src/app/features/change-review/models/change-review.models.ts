export interface SubmitChangeReviewResponse {
  reviewId: string;
  status: string;
  currentStage: string;
}

export interface ChangeReviewSummary {
  reviewId: string;
  applicationName: string;
  changeTitle: string;
  createdAt: string;
  status: string;
  riskLevel: string;
  recommendation: string;
}

export interface ChangeReview {
  reviewId: string;
  applicationName: string;
  changeTitle: string;
  changeDescription: string;
  targetEnvironment: string;
  implementationWindow: string;
  status: string;
  currentStage: string;
  classification: unknown;
  affectedObjects: unknown[];
  findings: Finding[];
  informationGaps: InformationGap[];
  clarificationQuestions: string[];
  riskAssessment: RiskAssessment | Record<string, never>;
  recommendation: string;
  humanReviewStatus: string;
  createdAt: string;
  completedAt: string;
}

export interface Finding {
  findingId: { value: string } | string;
  ruleCode: string;
  title: string;
  description: string;
  severity: string;
  category: string;
  evidenceIds: Array<{ value: string } | string>;
  requiredAction: string;
}

export interface InformationGap {
  gapId: { value: string } | string;
  description: string;
  reasonRequired: string;
  severity: string;
  relatedEvidenceIds: Array<{ value: string } | string>;
  suggestedQuestion: string;
  resolutionStatus: string;
  userAnswer?: string;
}

export interface RiskAssessment {
  numericalScore: number;
  riskLevel: string;
  explanation: string;
  criticalFindingCount: number;
  highFindingCount: number;
  missingInformationCount: number;
  evidenceCoverageScore: number;
}

export interface ReviewPlan {
  planId: { value: string } | string;
  orderedSteps: unknown[];
  requiredTools: string[];
  planStatus: string;
}

export interface ToolActivity {
  activityId: { value: string } | string;
  toolType: string;
  status: string;
  safeReasonSummary: string;
  startTime: string;
  completionTime?: string;
}

export interface ChangeAssuranceReport {
  identification: Record<string, string>;
  executiveSummary: string;
  agentUnderstanding: unknown;
  reviewPlan: unknown;
  findingsBySeverity: Record<string, Finding[]>;
  informationGaps: InformationGap[];
  riskScenarios: unknown[];
  riskAssessment: RiskAssessment;
  recommendation: { value: string; deterministicReasonCodes: string[] };
  requiredActions: string[];
  suggestedTests: string[];
  humanReviewQuestions: string[];
  criticReview: unknown;
  evidenceReferences: unknown[];
  limitations: string[];
  humanApprovalRequired: boolean;
  status: string;
  currentStage: string;
}

export interface SubmitChangeReviewRequest {
  applicationName: string;
  changeTitle: string;
  changeDescription: string;
  changeType: string;
  targetEnvironment: string;
  implementationWindow: string;
  deploymentPlan: string;
  rollbackPlan: string;
  testEvidence: string;
  sqlFile?: File | null;
}

export interface SubmitAnswerRequest {
  gapId: string;
  answer: string;
}

export function idValue(id: { value: string } | string | undefined): string {
  if (!id) return '';
  return typeof id === 'string' ? id : id.value;
}
