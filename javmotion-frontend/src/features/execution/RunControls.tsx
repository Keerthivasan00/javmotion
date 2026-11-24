import { useEffect } from 'react'
import { Loader2, Play, Square } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { useRunStore } from './runStore'
import { submitRun, useCancelRun, useIsRunning } from './useRunCode'

/** Run / Stop button. Ctrl+Enter (Cmd+Enter) runs from anywhere on the page. */
export function RunControls() {
  const submitting = useRunStore((s) => s.submitting)
  const cancel = useCancelRun()
  const busy = useIsRunning()

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Enter' || !(e.ctrlKey || e.metaKey) || e.shiftKey) return
      // Inside Monaco the editor's own Ctrl+Enter command runs instead (see CodeEditor onRun).
      if (e.target instanceof Element && e.target.closest('.monaco-editor')) return
      e.preventDefault()
      void submitRun()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  if (busy) {
    return (
      <Button
        size="sm"
        variant="outline"
        onClick={() => cancel.mutate()}
        disabled={submitting || cancel.isPending}
        title="Stop the running program"
      >
        {submitting ? (
          <Loader2 size={16} className="animate-spin" aria-hidden="true" />
        ) : (
          <Square size={14} aria-hidden="true" />
        )}
        {submitting ? 'Sending…' : 'Stop'}
      </Button>
    )
  }

  return (
    <Button size="sm" onClick={() => void submitRun()} title="Run (Ctrl+Enter)">
      <Play size={16} /> Run
    </Button>
  )
}
