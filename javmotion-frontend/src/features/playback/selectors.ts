import type { TraceStep, Value } from '@/lib/trace/types'
import { usePlaybackStore } from './playbackStore'

type State = ReturnType<typeof usePlaybackStore.getState>

export const selectCurrentStep = (s: State): TraceStep | undefined => s.trace?.steps[s.index]

export const selectPreviousStep = (s: State): TraceStep | undefined =>
  s.index > 0 ? s.trace?.steps[s.index - 1] : undefined

export const selectCurrentStdout = (s: State): string => selectCurrentStep(s)?.stdout ?? ''

export const selectStepCount = (s: State): number => s.trace?.steps.length ?? 0

/** Top frame (the method currently executing), or undefined at END. */
export const topFrame = (step: TraceStep | undefined) => step?.stack.at(-1)

export function sameValue(a: Value | undefined, b: Value | undefined): boolean {
  if (a === b) return true
  if (!a || !b || a.kind !== b.kind) return false
  if (a.kind === 'PRIMITIVE' && b.kind === 'PRIMITIVE')
    return a.value === b.value && a.type === b.type
  if (a.kind === 'REF' && b.kind === 'REF') return a.ref === b.ref
  return true // both NULL
}

/**
 * Names of top-frame locals that are new or changed since the previous step.
 * Only compares when both steps are in the same frame (a CALL/RETURN changes everything).
 * Pure function — callers memoize on (step, prev).
 */
export function changedLocals(
  step: TraceStep | undefined,
  prev: TraceStep | undefined,
): Set<string> {
  const changed = new Set<string>()
  const now = topFrame(step)
  if (!now) return changed
  const before = topFrame(prev)
  const sameFrame = before && before.id === now.id && prev!.stack.length === step!.stack.length

  for (const [name, value] of Object.entries(now.locals)) {
    if (!sameFrame || !sameValue(value, before.locals[name])) changed.add(name)
  }
  return changed
}

// Hooks — subscribe to the narrowest slice so a step change re-renders only readers.
export const useCurrentStep = () => usePlaybackStore(selectCurrentStep)
export const usePreviousStep = () => usePlaybackStore(selectPreviousStep)
export const useCurrentStdout = () => usePlaybackStore(selectCurrentStdout)
export const useHasTrace = () => usePlaybackStore((s) => s.trace !== null)
