import { create } from 'zustand'
import type { ExecutionMode } from './executionApi'

/** The run the Playground is showing. Server data itself lives in the TanStack Query cache. */
export interface CurrentRun {
  id: string
  token: string
  mode: ExecutionMode
}

/**
 * Shared by every component that shows run state (button, status bar, console, editor markers).
 * Submit state lives here rather than in a per-component useMutation, so all of them agree.
 */
interface RunState {
  run: CurrentRun | null
  submitting: boolean
  submitError: unknown
  setRun: (run: CurrentRun | null) => void
  setSubmitting: (submitting: boolean) => void
  setSubmitError: (error: unknown) => void
}

export const useRunStore = create<RunState>((set) => ({
  run: null,
  submitting: false,
  submitError: null,
  setRun: (run) => set({ run }),
  setSubmitting: (submitting) => set({ submitting }),
  setSubmitError: (submitError) => set({ submitError }),
}))
