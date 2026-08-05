import { spawn } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

import { isPurchaseCondition, type PurchaseCondition } from "./research-session.ts";

const DEFAULT_TIMEOUT_MS = 90_000;
const MAX_OUTPUT_BYTES = 1_000_000;
let activeExecutions = 0;

/** CodexCommandRunner는 실제 process와 테스트 대역이 공유하는 실행 경계다. */
export type CodexCommandRunner = (
  args: string[],
  input: string,
  options: { cwd: string; timeoutMs: number },
) => Promise<string>;

/** CodexRuntimeError는 AI 실행 실패 유형을 API 상태로 변환할 수 있게 보존한다. */
export class CodexRuntimeError extends Error {
  readonly code: "AI_UNAVAILABLE" | "AI_OUTPUT_INVALID";

  /** AI 실행 오류 코드와 사용자에게 노출 가능한 설명을 생성한다. */
  constructor(code: "AI_UNAVAILABLE" | "AI_OUTPUT_INVALID", message: string) {
    super(message);
    this.code = code;
    this.name = "CodexRuntimeError";
  }
}

/** 저장소 표식 파일을 찾을 때까지 상위 디렉토리를 탐색한다. */
function findRepositoryRoot(start = process.cwd()): string {
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
    const child = spawn(process.env.CODEX_CLI_PATH ?? "codex", args, {
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
        finish(new CodexRuntimeError("AI_UNAVAILABLE", `Codex 실행이 실패했습니다. ${stderr.trim()}`.trim()));
      }
    });
    child.stdin.end(input);
  });
}

/** 사용자 질문을 Plugin 규칙과 공통 Schema에 따라 구매 조건 JSON으로 구조화한다. */
export async function structurePurchaseQuestion(
  question: string,
  runner: CodexCommandRunner = runCodexCommand,
): Promise<PurchaseCondition> {
  const root = findRepositoryRoot();
  const schemaPath = path.join(root, "contracts/research/v1/purchase-condition.schema.json");
  const skillPath = path.join(root, "plugins/purchase-research-agent/skills/purchase-research/SKILL.md");
  const pluginRules = readFileSync(skillPath, "utf8");
  const prompt = [
    "당신은 Purchase Research Agent의 구매 조건 구조화 단계다.",
    "아래 Plugin 규칙을 적용하되 상품을 검색하거나 사실을 추측하지 않는다.",
    pluginRules,
    "사용자 질문에 명시되지 않았지만 결과를 크게 바꾸는 조건은 missingConditions에 기록한다.",
    "assumptions에는 추론한 내용만 기록하고 requiresConfirmation은 반드시 true로 둔다.",
    "사용자 질문:",
    question,
  ].join("\n\n");
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
  return parsed;
}
