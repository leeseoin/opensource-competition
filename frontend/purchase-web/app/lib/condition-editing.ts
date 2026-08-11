import type { ConditionPriority, PrioritizedText } from "./research-session.ts";

/** parsePrioritizedList는 사용자가 목록 값을 교체해도 기존 조건 묶음의 강도를 보존한다. */
export function parsePrioritizedList(
  value: string,
  current: PrioritizedText[],
  fallbackPriority: ConditionPriority,
): PrioritizedText[] {
  const priorityByValue = new Map(current.map((item) => [item.value, item.priority]));
  const inheritedPriority = current[0]?.priority ?? fallbackPriority;
  return value.split(",").map((item) => item.trim()).filter(Boolean).map((item) => ({
    value: item,
    priority: priorityByValue.get(item) ?? inheritedPriority,
  }));
}
