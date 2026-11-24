import { CheckCircle2, CircleAlert, Clock, Loader2 } from 'lucide-react'
import { useDelayedFlag } from '@/lib/utils/useDelayedFlag'
import { cn } from '@/lib/utils/cn'
import { requestErrorMessage, statusSummary, type Tone } from './runMessages'
import { useRunStore } from './runStore'
import { useExecutionStatus, useIsRunning } from './useRunCode'

const TONE: Record<Tone, string> = {
  muted: 'text-muted',
  success: 'text-success',
  error: 'text-error',
  warning: 'text-warning',
}

/** Compact status of the current run, shown in the editor toolbar. */
export function ExecutionStatusBar() {
  const submitting = useRunStore((s) => s.submitting)
  const submitError = useRunStore((s) => s.submitError)
  const status = useExecutionStatus()
  const running = useIsRunning()
  const waking = useDelayedFlag(submitting || (running && status.data?.status === 'QUEUED'))

  let content: { text: string; tone: Tone } | null = null
  if (submitError) content = { text: requestErrorMessage(submitError), tone: 'error' }
  else if (status.isError) content = { text: requestErrorMessage(status.error), tone: 'error' }
  else if (waking) content = { text: 'Waking up server…', tone: 'muted' }
  else if (submitting) content = { text: 'Sending…', tone: 'muted' }
  else if (status.data) content = statusSummary(status.data)

  if (!content) return null
  const Icon =
    content.tone === 'success'
      ? CheckCircle2
      : content.tone === 'muted'
        ? running
          ? Loader2
          : Clock
        : CircleAlert

  return (
    <span
      role="status"
      aria-live="polite"
      className={cn('flex min-w-0 items-center gap-1.5 text-xs', TONE[content.tone])}
    >
      <Icon
        size={14}
        aria-hidden="true"
        className={cn('shrink-0', Icon === Loader2 && 'animate-spin')}
      />
      <span className="truncate">{content.text}</span>
    </span>
  )
}
