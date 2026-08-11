"use client";

import * as Select from "@radix-ui/react-select";

import styles from "./app-select.module.css";

export interface AppSelectOption {
  value: string;
  label: string;
}

interface AppSelectProps {
  ariaLabel: string;
  value: string;
  options: AppSelectOption[];
  onValueChange(value: string): void;
  tone?: "paper" | "lime";
  disabled?: boolean;
}

/** AppSelect는 접근 가능한 키보드 동작과 프로젝트 공통 시각 규칙을 제공한다. */
export function AppSelect({
  ariaLabel,
  value,
  options,
  onValueChange,
  tone = "paper",
  disabled = false,
}: AppSelectProps) {
  return (
    <Select.Root value={value} onValueChange={onValueChange} disabled={disabled}>
      <Select.Trigger
        className={`${styles.trigger} ${styles[tone]}`}
        aria-label={ariaLabel}
      >
        <Select.Value />
        <Select.Icon className={styles.icon} aria-hidden="true">⌄</Select.Icon>
      </Select.Trigger>
      <Select.Portal>
        <Select.Content
          className={styles.content}
          position="popper"
          sideOffset={6}
          collisionPadding={12}
        >
          <Select.ScrollUpButton className={styles.scrollButton}>↑</Select.ScrollUpButton>
          <Select.Viewport className={styles.viewport}>
            {options.map((option) => (
              <Select.Item className={styles.item} value={option.value} key={option.value}>
                <Select.ItemText>{option.label}</Select.ItemText>
                <Select.ItemIndicator className={styles.indicator}>✓</Select.ItemIndicator>
              </Select.Item>
            ))}
          </Select.Viewport>
          <Select.ScrollDownButton className={styles.scrollButton}>↓</Select.ScrollDownButton>
        </Select.Content>
      </Select.Portal>
    </Select.Root>
  );
}
