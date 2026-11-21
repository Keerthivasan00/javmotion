import { useState } from 'react'
import { useCurrentStdout, useHasTrace } from '@/features/playback'
import { cn } from '@/lib/utils/cn'

type Tab = 'output' | 'errors'

/**
 * While a trace is loaded, Output shows stdout up to the current playback step.
 * TODO (Step 6): show run output, compile errors, status, duration.
 */
export function ConsolePanel() {
  const [tab, setTab] = useState<Tab>('output')
  const hasTrace = useHasTrace()
  const stdout = useCurrentStdout()

  return (
    <section className="flex h-full flex-col bg-surface" aria-label="Console">
      <div className="flex h-9 shrink-0 items-center gap-1 border-b px-2" role="tablist">
        {(['output', 'errors'] as const).map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={cn(
              'rounded px-3 py-1 text-xs font-medium capitalize',
              tab === t ? 'bg-surface-2 text-fg' : 'text-muted hover:text-fg',
            )}
          >
            {t}
          </button>
        ))}
      </div>
      <pre
        className={cn(
          'min-h-0 flex-1 overflow-auto p-3 font-mono text-sm',
          tab === 'output' && hasTrace && stdout ? 'text-fg' : 'text-muted',
        )}
      >
        {tab === 'errors'
          ? 'No errors.'
          : hasTrace
            ? stdout || '(no output yet at this step)'
            : 'Program output will appear here.'}
      </pre>
    </section>
  )
}
