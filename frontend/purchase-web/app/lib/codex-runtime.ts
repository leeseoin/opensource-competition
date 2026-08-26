import { spawn } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

import { isPurchaseCondition, type PurchaseCondition } from "./research-session.ts";
import { AgentRuntimeError, type AgentCommandRunner } from "./agent-runtime.ts";

const DEFAULT_TIMEOUT_MS = 90_000;
const MAX_OUTPUT_BYTES = 1_000_000;
let activeExecutions = 0;

/** CodexCommandRunner는 실제 process와 테스트 대역이 공유하는 실행 경계다. */
export type CodexCommandRunner = AgentCommandRunner;

/** CodexRuntimeError는 AI 실행 실패 유형을 API 상태로 변환할 수 있게 보존한다. */
export class CodexRuntimeError extends AgentRuntimeError {
  /** AI 실행 오류 코드와 사용자에게 노출 가능한 설명을 생성한다. */
  constructor(code: "AI_UNAVAILABLE" | "AI_OUTPUT_INVALID" | "AI_AUTH_REQUIRED", message: string) {
    super(code, message);
    this.name = "CodexRuntimeError";
  }
}

/** resolveCodexCommand는 빈 환경변수가 실행 파일 기본값을 무력화하지 않게 한다. */
export function resolveCodexCommand(configured = process.env.CODEX_CLI_PATH): string {
  return configured?.trim() || "codex";
}

/** classifyCodexProcessFailure는 민감한 stderr를 노출하지 않고 인증 실패와 일반 실패를 구분한다. */
export function classifyCodexProcessFailure(stderr: string): CodexRuntimeError {
  const normalized = stderr.toLowerCase();
  if (normalized.includes("invalid_json_schema")) {
    return new CodexRuntimeError(
      "AI_OUTPUT_INVALID",
      "Codex 구조화 출력 Schema가 현재 CLI 규격과 일치하지 않습니다.",
    );
  }
  const authenticationFailed = [
    "token_invalidated",
    "token_revoked",
    "refresh_token_invalidated",
    "refresh token was revoked",
    "please log out and sign in again",
  ].some((indicator) => normalized.includes(indicator));
  if (authenticationFailed) {
    return new CodexRuntimeError(
      "AI_AUTH_REQUIRED",
      "Codex 로그인이 만료되었습니다. 서버에서 codex logout 후 codex login을 실행해 주세요.",
    );
  }
  return new CodexRuntimeError("AI_UNAVAILABLE", "Codex CLI 실행에 실패했습니다. 서버 로그를 확인해 주세요.");
}

/** 저장소 표식 파일을 찾을 때까지 상위 디렉토리를 탐색한다. */
export function findRepositoryRoot(start = process.cwd()): string {
  if (process.env.PURCHASE_RESEARCH_REPO_ROOT) {
    return path.resolve(process.env.PURCHASE_RESEARCH_REPO_ROOT);
  }
  let current = path.resolve(start);
  while (true) {
    if (existsSync(path.join(current, "contracts/research/v1/purchase-condition.schema.json"))) {
      return current;
    }
    const parent = path.dirname(current);
    if (parent === current) {
      throw new CodexRuntimeError("AI_UNAVAILABLE", "프로젝트 루트를 찾을 수 없습니다.");
    }
    current = parent;
  }
}

/** 환경 변수에서 Codex 실행 제한 시간을 읽고 안전한 범위로 제한한다. */
function getTimeoutMs(): number {
  const configured = Number(process.env.CODEX_GATEWAY_TIMEOUT_MS);
  if (!Number.isInteger(configured) || configured < 1_000) {
    return DEFAULT_TIMEOUT_MS;
  }
  return Math.min(configured, 180_000);
}

/** 실제 Codex CLI를 shell 없이 실행하고 최종 JSON stdout만 반환한다. */
export function runCodexCommand(
  args: string[],
  input: string,
  options: { cwd: string; timeoutMs: number },
): Promise<string> {
  return new Promise((resolve, reject) => {
    const allowedEnvironment = ["PATH", "HOME", "CODEX_HOME", "OPENAI_API_KEY", "LANG", "LC_ALL", "TMPDIR"];
    const environment: NodeJS.ProcessEnv = { NODE_ENV: process.env.NODE_ENV ?? "production" };
    for (const key of allowedEnvironment) {
      if (process.env[key]) {
        environment[key] = process.env[key];
      }
    }
    const child = spawn(resolveCodexCommand(), args, {
      cwd: options.cwd,
      env: environment,
      shell: false,
      stdio: ["pipe", "pipe", "pipe"],
    });
    let stdout = "";
    let stderr = "";
    let settled = false;

    /** process 종료 결과를 한 번만 resolve 또는 reject한다. */
    function finish(error?: Error): void {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timer);
      if (error) {
        reject(error);
      } else {
        resolve(stdout);
      }
    }

    const timer = setTimeout(() => {
      child.kill("SIGTERM");
      finish(new CodexRuntimeError("AI_UNAVAILABLE", "Codex 응답 제한 시간을 초과했습니다."));
    }, options.timeoutMs);

    child.stdout.on("data", (chunk: Buffer) => {
      stdout += chunk.toString("utf8");
      if (Buffer.byteLength(stdout) > MAX_OUTPUT_BYTES) {
        child.kill("SIGTERM");
        finish(new CodexRuntimeError("AI_OUTPUT_INVALID", "Codex 응답 크기가 제한을 초과했습니다."));
      }
    });
    child.stderr.on("data", (chunk: Buffer) => {
      stderr = (stderr + chunk.toString("utf8")).slice(-4_000);
    });
    child.on("error", () => finish(new CodexRuntimeError("AI_UNAVAILABLE", "Codex CLI를 실행할 수 없습니다.")));
    child.on("close", (code) => {
      if (code === 0) {
        finish();
      } else {
        const error = classifyCodexProcessFailure(stderr);
        console.error(`[codex-runtime] process failed: code=${error.code}, exitCode=${code ?? "unknown"}`);
        finish(error);
      }
    });
    child.stdin.end(input);
  });
}

/** 사용자 질문에서 특정 색상을 선호로 완화한 표현인지 해당 색상 주변 문맥으로 판정한다. */
function isSoftColorPreference(question: string, color: string): boolean {
  const normalizedQuestion = question.replace(/\s+/g, " ");
  const colorIndex = normalizedQuestion.toLowerCase().indexOf(color.trim().toLowerCase());
  if (colorIndex < 0) {
    return true;
  }
  const context = normalizedQuestion.slice(
    Math.max(0, colorIndex - 12),
    Math.min(normalizedQuestion.length, colorIndex + color.length + 30),
  );
  if (/(다른\s*색|색(?:은|도)?\s*달라도)[^.!?]{0,8}(안\s*(돼|됨|괜찮)|싫|불가)/.test(context)) {
    return false;
  }
  return /(이면|이었으면|였으면)\s*(좋|괜찮)|선호|가능하면|가급적|되도록|우선|상관없|아니어도|없으면|달라도|다른\s*색/.test(context);
}

/** 미확인 상품 종류 대신 숫자 포함 모델명으로 기록된 첫 필수 조건을 검색어로 승격한다. */
function promoteProductModelRequirement(condition: PurchaseCondition): PurchaseCondition {
  const productType = condition.productType.value.trim();
  if (!/^(미확인|알\s*수\s*없음|unknown|상품)$/i.test(productType)) {
    return condition;
  }
  const modelIndex = condition.requirements.findIndex((item) =>
    item.priority === "required" && /\p{L}/u.test(item.value) && /\p{N}/u.test(item.value));
  if (modelIndex < 0) {
    return condition;
  }
  const model = condition.requirements[modelIndex];
  return {
    ...condition,
    productType: {
      ...condition.productType,
      value: model.value.trim(),
    },
    requirements: condition.requirements.filter((_, index) => index !== modelIndex),
  };
}

/** 명시 색상의 강도와 productType의 색상 중복을 검색 정책에 맞게 보정한다. */
export function normalizePurchaseCondition(condition: PurchaseCondition, question: string): PurchaseCondition {
  const promoted = promoteProductModelRequirement(condition);
  let productType = promoted.productType.value.trim();
  for (const color of promoted.colors) {
    const token = color.value.trim();
    if (!token) {
      continue;
    }
    productType = productType.replaceAll(token, " ");
  }
  productType = productType.replace(/\s+/g, " ").trim();
  return {
    ...promoted,
    colors: promoted.colors.map((color) => ({
      ...color,
      priority: isSoftColorPreference(question, color.value) ? "preferred" : "required",
    })),
    productType: {
      ...promoted.productType,
      value: productType || promoted.productType.value.trim(),
    },
  };
}

/** 사용자 질문을 Plugin 규칙과 공통 Schema에 따라 구매 조건 JSON으로 구조화한다. */
/** buildPurchaseConditionPrompt는 모든 CLI runtime에 동일한 Plugin 규칙과 사용자 질문을 제공한다. */
export function buildPurchaseConditionPrompt(question: string, pluginRules: string): string {
  return [
    "당신은 Purchase Research Agent의 구매 조건 구조화 단계다.",
    "아래 Plugin 규칙을 적용하되 상품을 검색하거나 사실을 추측하지 않는다.",
    pluginRules,
    "productType에는 구두, 운동화처럼 상품 종류만 기록한다.",
    "상품 종류를 모르는 특정 모델명 요청이면 미확인으로 두지 말고 모델명을 productType에 기록한다.",
    "색상, 사이즈, 가격, 판매처와 용도는 각각의 전용 필드에만 기록하고 productType에 중복하지 않는다.",
    "각 조건의 priority는 구매 불가능 조건이면 required, 가능하면 만족할 선호이면 preferred로 기록한다.",
    "productType은 required, 용도는 기본 preferred, 사용자가 명시한 사이즈와 가격 상한은 기본 required로 기록한다.",
    "사용자가 색상을 단정해 요청하면 required로 기록하고, '이면 좋겠어', '선호', '가능하면'처럼 완화를 표현한 경우에만 preferred로 기록한다.",
    "사용자 질문에 명시되지 않았지만 결과를 크게 바꾸는 조건은 missingConditions에 기록한다.",
    "assumptions에는 추론한 내용만 기록하고 requiresConfirmation은 반드시 true로 둔다.",
    "normalizedValue, canonicalId, confidence, derivedBy를 직접 확정할 근거가 없으면 null로 기록한다.",
    "개별 조건의 requiresConfirmation은 불확실하면 true, 원문을 그대로 옮겼으면 false로 기록한다.",
    "범용 attributes가 없으면 빈 배열로 기록한다.",
    "사용자 질문:",
    question,
  ].join("\n\n");
}

export async function structurePurchaseQuestion(
  question: string,
  runner: CodexCommandRunner = runCodexCommand,
): Promise<PurchaseCondition> {
  const root = findRepositoryRoot();
  const schemaPath = path.join(root, "contracts/research/v1/purchase-condition.codex-output.schema.json");
  const skillPath = path.join(root, "plugins/purchase-research-agent/skills/purchase-research/SKILL.md");
  const pluginRules = readFileSync(skillPath, "utf8");
  const prompt = buildPurchaseConditionPrompt(question, pluginRules);
  let output: string;
  if (activeExecutions >= 1) {
    throw new CodexRuntimeError("AI_UNAVAILABLE", "다른 Codex 구매 조건 요청을 처리하고 있습니다.");
  }
  activeExecutions += 1;
  try {
    output = await runner([
      "-a", "never", "exec", "--ephemeral", "--sandbox", "read-only",
      "--output-schema", schemaPath, "-C", root, "-",
    ], prompt, { cwd: root, timeoutMs: getTimeoutMs() });
  } catch (error) {
    if (error instanceof CodexRuntimeError) {
      throw error;
    }
    throw new CodexRuntimeError("AI_UNAVAILABLE", "Codex가 구매 조건을 생성하지 못했습니다.");
  } finally {
    activeExecutions -= 1;
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(output);
  } catch {
    throw new CodexRuntimeError("AI_OUTPUT_INVALID", "Codex가 올바른 JSON을 반환하지 않았습니다.");
  }
  if (!isPurchaseCondition(parsed)) {
    throw new CodexRuntimeError("AI_OUTPUT_INVALID", "Codex 응답이 PurchaseCondition 계약과 일치하지 않습니다.");
  }
  return normalizePurchaseCondition(parsed, question);
}
