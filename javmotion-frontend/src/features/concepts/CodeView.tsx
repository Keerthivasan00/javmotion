import { useEffect, useRef } from 'react'
import { cn } from '@/lib/utils/cn'

/**
 * Read-only code with line numbers and the current line highlighted.
 * Lighter than Monaco for the Concepts page, where code is never edited.
 */
export function CodeView({ code, currentLine }: { code: string; currentLine?: number }) {
  const lines = code.replace(/\n$/, '').split('\n')
  const activeRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    activeRef.current?.scrollIntoView?.({ block: 'nearest' })
  }, [currentLine])

  return (
    <pre
      className="overflow-auto rounded-lg border bg-surface py-3 font-mono text-[13px] leading-6"
      aria-label="Program code"
    >
      {lines.map((text, i) => {
        const n = i + 1
        const active = n === currentLine
        return (
          <div
            key={n}
            ref={active ? activeRef : undefined}
            data-line={n}
            aria-current={active ? 'step' : undefined}
            className={cn(
              'flex border-l-2 pr-4',
              active ? 'border-primary bg-primary/15' : 'border-transparent',
            )}
          >
            <span className="w-10 shrink-0 pr-3 text-right text-muted select-none">{n}</span>
            <code className="whitespace-pre">{text || ' '}</code>
          </div>
        )
      })}
    </pre>
  )
}
