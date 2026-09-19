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
  preferredAiModel?: string;
  preferredAiProvider?: string;
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
  /** 1-based line in the uploaded SQL when evidence is a SQL statement */
  sqlLine?: number | null;
  /** SQL excerpt for the finding */
  sqlSnippet?: string | null;
  sqlSourceReference?: string | null;
}

export interface EvidenceReference {
  evidenceId: { value: string } | string;
  evidenceType: string;
  source?: string;
  sourceReference?: string;
  description?: string;
  extractedContent?: string | null;
  lineNumber?: number | null;
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
  ruleVersion?: string;
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

export interface RiskScenario {
  scenarioId: { value: string } | string;
  title: string;
  description: string;
  eventSequence?: string[];
  supportingEvidenceIds?: Array<{ value: string } | string>;
  contradictingEvidenceIds?: Array<{ value: string } | string>;
  confidence?: number;
  requiredValidation?: string;
  hypothesis?: boolean;
}

export interface CriticReview {
  availabilityStatus?: string;
  accepted?: boolean;
  unsupportedClaims?: string[];
  missingDeterministicFindings?: string[];
  contradictions?: string[];
  weakEvidenceReferences?: string[];
  attemptedRecommendationOverride?: boolean;
  summary?: string;
}

export interface AiContributionTask {
  taskName: string;
  purpose: string;
  howExecuted: string;
  outcome: string;
}

export interface AiContributionSection {
  summary: string;
  selectedProvider?: string;
  selectedModel?: string;
  promptVersion?: string;
  draftReportStatus?: string;
  criticStatus?: string;
  liveModelTasks?: number;
  cannotOverride?: string[];
  tasks?: AiContributionTask[];
}

export interface ChangeAssuranceReport {
  identification: Record<string, string>;
  executiveSummary: string;
  agentUnderstanding?: AgentUnderstanding;
  databaseImpact?: DatabaseImpactSection;
  reviewPlan: unknown;
  findingsBySeverity: Record<string, Finding[]>;
  informationGaps: InformationGap[];
  riskScenarios: RiskScenario[] | unknown[];
  riskAssessment: RiskAssessment | Record<string, never>;
  recommendation: { value: string; deterministicReasonCodes: string[] };
  requiredActions: string[];
  suggestedTests: string[];
  humanReviewQuestions: string[];
  criticReview: CriticReview | unknown;
  aiContribution?: AiContributionSection;
  evidenceReferences: EvidenceReference[] | unknown[];
  limitations: string[];
  humanApprovalRequired: boolean;
  status: string;
  currentStage: string;
}

export interface AgentUnderstanding {
  classification?: unknown;
  affectedObjects?: AffectedObjectView[];
  aiAvailability?: string;
}

export interface AffectedObjectView {
  objectName: string;
  objectType: string;
  schemaName?: string | null;
  changeKind?: string | null;
}

export interface DatabaseImpactDeployChange {
  specChanged?: boolean;
  bodyChanged?: boolean;
  changeKind?: string;
  inferredFromScript?: boolean;
  packagesInScript?: string[];
}

export interface DatabaseImpactObject {
  owner: string;
  objectName: string;
  objectType: string;
  status: string;
  qualifiedName: string;
  depth?: number;
}

export interface DatabaseImpactSourceRef {
  owner: string;
  objectName: string;
  objectType: string;
  line: number;
  excerpt: string;
  qualifiedName: string;
}

export interface DatabaseImpactJob {
  owner: string;
  jobName: string;
  jobType: string;
  enabled: string;
  state: string;
  actionExcerpt: string;
  qualifiedName: string;
}

export interface DatabaseImpactSection {
  available: boolean;
  packageFound?: boolean;
  catalogMode?: string;
  summary?: string;
  message?: string;
  requestedPackage?: string;
  schemaOwner?: string;
  overallImpact?: string;
  why?: string[];
  recommendedTesting?: string[];
  impactSummary?: string;
  deployChange?: DatabaseImpactDeployChange;
  package?: {
    owner: string;
    name: string;
    qualifiedName: string;
    specPresent: boolean;
    bodyPresent: boolean;
    status: string;
  };
  procedures?: Array<{ name: string; procedureType: string }>;
  dependencies?: DatabaseImpactObject[];
  dependents?: DatabaseImpactObject[];
  transitiveDependents?: DatabaseImpactObject[];
  sourceReferences?: DatabaseImpactSourceRef[];
  schedulerJobs?: DatabaseImpactJob[];
  invalidRelatedObjects?: DatabaseImpactObject[];
  dependencyCountsByType?: Record<string, number>;
  dependentCountsByType?: Record<string, number>;
  blastRadius?: {
    dependentCount: number;
    dependencyCount: number;
    procedureCount: number;
    transitiveCount?: number;
    schedulerJobCount?: number;
    invalidRelatedCount?: number;
    sourceReferenceCount?: number;
  };
}

export interface SubmitChangeReviewRequest {
  packageName?: string;
  schemaOwner?: string;
  applicationName?: string;
  changeTitle?: string;
  changeDescription?: string;
  changeType?: string;
  targetEnvironment?: string;
  implementationWindow?: string;
  deploymentPlan?: string;
  rollbackPlan?: string;
  testEvidence?: string;
  aiProvider?: string;
  aiModel?: string;
  sqlFile?: File | null;
}

export interface AiModelOption {
  id: string;
  label: string;
  isDefault: boolean;
}

export interface AiProviderOption {
  id: string;
  label: string;
  available: boolean;
  baseUrl: string;
  defaultModel: string;
  allowsCustomModel: boolean;
  models: AiModelOption[];
}

export interface AiConfig {
  available: boolean;
  mode: string;
  defaultProvider: string;
  defaultModel: string;
  baseUrl: string;
  allowsCustomModel: boolean;
  models: AiModelOption[];
  providers: AiProviderOption[];
}

export interface SubmitAnswerRequest {
  gapId: string;
  answer: string;
}

export function idValue(id: { value: string } | string | undefined): string {
  if (!id) return '';
  return typeof id === 'string' ? id : id.value;
}
