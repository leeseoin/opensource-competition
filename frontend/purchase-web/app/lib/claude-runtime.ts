import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";

import { AgentRuntimeError, type AgentCommandRunner } from "./agent-runtime.ts";
import {
  buildPurchaseConditionPrompt,
  findRepositoryRoot,
  normalizePurchaseCondition,
} from "./codex-runtime.ts";
import { isPurchaseCondition, type PurchaseCondition } from "./research-session.ts";

const DEFAULT_TIMEOUT_MS = 90_000;
const MAX_OUTPUT_BYTES = 1_000_000;
let activeExecutions = 0;

/** ClaudeCommandRunner는 Claude CLI process와 테스트 대역이 공유하는 실행 경계다. */
export type ClaudeCommandRunner = AgentCommandRunner;

/** resolveClaudeCommand는 빈 환경변수가 실행 파일 기본값을 무력화하지 않게 한다. */
export function resolveClaudeCommand(configured = process.env.CLAUDE_CLI_PATH): string {
  return configured?.trim() || "claude";
}

/** classifyClaudeProcessFailure는 stderr 원문을 숨긴 채 인증, Schema와 일반 실패를 구분한다. */
export function classifyClaudeProcessFailure(stderr: string): AgentRuntimeError {
  const normalized = stderr.toLowerCase();
  if (["json schema", "json-schema", "structured output"].some((value) => normalized.includes(value))) {
    return new AgentRuntimeError(
      "AI_OUTPUT_INVALID",
      "Claude 구조화 출력 Schema가 현재 CLI 규격과 일치하지 않습니다.",
    );
  }
  if ([
    "not logged in",
    "please run /login",
    "authentication failed",
    "invalid api key",
    "oauth token",
  ].some((value) => normalized.includes(value))) {
    return new AgentRuntimeError(
      "AI_AUTH_REQUIRED",
      "Claude 로그인이 필요합니다. 서버에서 claude를 실행한 뒤 /login으로 인증해 주세요.",
    );
  }
  return new AgentRuntimeError("AI_UNAVAILABLE", "Claude Code CLI 실행에 실패했습니다. 서버 로그를 확인해 주세요.");
}

/** 환경 변수에서 Claude 실행 제한 시간을 읽고 안전한 범위로 제한한다. */
function getTimeoutMs(): number {
  const configured = Number(process.env.CLAUDE_GATEWAY_TIMEOUT_MS);
  if (!Number.isInteger(configured) || configured < 1_000) {
    return DEFAULT_TIMEOUT_MS;
  }
  return Math.min(configured, 180_000);
}

/** 실제 Claude Code CLI를 shell과 파일 도구 없이 실행하고 JSON stdout만 반환한다. */
export function runClaudeCommand(
  args: string[],
  input: string,
  options: { cwd: string; timeoutMs: number },
): Promise<string> {
  return new Promise((resolve, reject) => {
    const allowedEnvironment = [
      "PATH", "HOME", "CLAUDE_CONFIG_DIR", "ANTHROPIC_API_KEY", "LANG", "LC_ALL", "TMPDIR",
    ];
    const environment: NodeJS.ProcessEnv = { NODE_ENV: process.env.NODE_ENV ?? "production" };
    for (const key of allowedEnvironment) {
      if (process.env[key]) {
        environment[key] = process.env[key];
      }
    }
    const child = spawn(resolveClaudeCommand(), args, {
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
      finish(new AgentRuntimeError("AI_UNAVAILABLE", "Claude 응답 제한 시간을 초과했습니다."));
    }, options.timeoutMs);
    child.stdout.on("data", (chunk: Buffer) => {
      stdout += chunk.toString("utf8");
      if (Buffer.byteLength(stdout) > MAX_OUTPUT_BYTES) {
        child.kill("SIGTERM");
        finish(new AgentRuntimeError("AI_OUTPUT_INVALID", "Claude 응답 크기가 제한을 초과했습니다."));
      }
    });
    child.stderr.on("data", (chunk: Buffer) => {
      stderr = (stderr + chunk.toString("utf8")).slice(-4_000);
    });
    child.on("error", () => finish(new AgentRuntimeError("AI_UNAVAILABLE", "Claude Code CLI를 실행할 수 없습니다.")));
    child.on("close", (code) => {
      if (code === 0) {
        finish();
      } else {
        const error = classifyClaudeProcessFailure(`${stderr}\n${stdout}`);
        console.error(`[claude-runtime] process failed: code=${error.code}, exitCode=${code ?? "unknown"}`);
        finish(error);
      }
    });
    child.stdin.end(input);
  });
}

/** Claude JSON envelope에서 Schema로 검증된 구조화 결과만 꺼낸다. */
function parseClaudeStructuredOutput(output: string): unknown {
  const envelope = JSON.parse(output) as Record<string, unknown>;
  if (envelope.structured_output && typeof envelope.structured_output === "object") {
    return envelope.structured_output;
  }
  if (typeof envelope.result === "string") {
    return JSON.parse(envelope.result);
  }
  return envelope;
}

/** 사용자 질문을 Claude Code와 공통 Plugin 규칙에 따라 구매 조건 JSON으로 구조화한다. */
export async function structurePurchaseQuestionWithClaude(
  question: string,
  runner: ClaudeCommandRunner = runClaudeCommand,
): Promise<PurchaseCondition> {
  const root = findRepositoryRoot();
  const schemaPath = path.join(root, "contracts/research/v1/purchase-condition.codex-output.schema.json");
  const skillPath = path.join(root, "plugins/purchase-research-agent/skills/purchase-research/SKILL.md");
  const schemaDocument = JSON.parse(readFileSync(schemaPath, "utf8")) as Record<string, unknown>;
  delete schemaDocument.$schema;
  const schema = JSON.stringify(schemaDocument);
  const prompt = buildPurchaseConditionPrompt(question, readFileSync(skillPath, "utf8"));
  const args = [
    "--print",
    "--output-format", "json",
    "--json-schema", schema,
    "--no-session-persistence",
    "--permission-mode", "dontAsk",
    "--tools", "",
  ];
  if (activeExecutions >= 1) {
    throw new AgentRuntimeError("AI_UNAVAILABLE", "다른 Claude 구매 조건 요청을 처리하고 있습니다.");
  }
  activeExecutions += 1;
  let output: string;
  try {
    output = await runner(args, prompt, { cwd: root, timeoutMs: getTimeoutMs() });
  } catch (error) {
    if (error instanceof AgentRuntimeError) {
      throw error;
    }
    throw new AgentRuntimeError("AI_UNAVAILABLE", "Claude가 구매 조건을 생성하지 못했습니다.");
  } finally {
    activeExecutions -= 1;
  }

  let parsed: unknown;
  try {
    parsed = parseClaudeStructuredOutput(output);
  } catch {
    throw new AgentRuntimeError("AI_OUTPUT_INVALID", "Claude가 올바른 구조화 JSON을 반환하지 않았습니다.");
  }
  if (!isPurchaseCondition(parsed)) {
    throw new AgentRuntimeError("AI_OUTPUT_INVALID", "Claude 응답이 PurchaseCondition 계약과 일치하지 않습니다.");
  }
  return normalizePurchaseCondition(parsed, question);
}
