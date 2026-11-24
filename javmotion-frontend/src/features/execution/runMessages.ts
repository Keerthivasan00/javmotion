import { ApiError } from '@/lib/api/errors'
import type { ExecutionStatus, ExecutionStatusResponse } from './executionApi'

export type Tone = 'muted' | 'success' | 'error' | 'warning'

/** One-line summary for the status bar. */
export function statusSummary(s: ExecutionStatusResponse): { text: string; tone: Tone } {
  const ms = s.executionTimeMs != null ? ` · ${formatMs(s.executionTimeMs)}` : ''
  const byStatus: Record<ExecutionStatus, { text: string; tone: Tone }> = {
    QUEUED: { text: 'Queued…', tone: 'muted' },
    RUNNING: { text: 'Running…', tone: 'muted' },
    COMPLETED: { text: `Finished${ms}`, tone: 'success' },
    COMPILE_ERROR: { text: 'Compilation failed', tone: 'error' },
    RUNTIME_ERROR: { text: `Runtime error (exit code ${s.exitCode ?? '?'})${ms}`, tone: 'error' },
    TIMEOUT: { text: s.errorMessage ?? 'Time limit exceeded', tone: 'warning' },
    LIMIT_EXCEEDED: { text: s.errorMessage ?? 'Limit exceeded', tone: 'warning' },
    FAILED: { text: s.errorMessage ?? 'Could not run the program', tone: 'error' },
    CANCELLED: { text: 'Stopped', tone: 'muted' },
  }
  return byStatus[s.status]
}

/** Message for a failed submit or poll. */
export function requestErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.isNetworkError) return 'Could not reach the server. Is javmotion-api running?'
    if (error.status === 429) return 'Too many runs — please wait a moment and try again.'
    if (error.code === 'SOURCE_TOO_LARGE' || error.code === 'STDIN_TOO_LARGE') return error.message
    const first = Object.values(error.fieldErrors)[0]
    if (first) return first
    return error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong.'
}

export function formatMs(ms: number): string {
  return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`
}
