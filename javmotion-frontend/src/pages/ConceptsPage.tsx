import { useEffect, useState } from 'react'
import { Navigate, NavLink, useParams } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import {
  CodeView,
  ConceptForm,
  DEFAULT_REQUEST,
  useConceptTrace,
  type ConceptKind,
  type ConceptRequest,
  type ConceptResult,
} from '@/features/concepts'
import { ConsolePanel } from '@/features/console'
import { usePlaybackStore } from '@/features/playback'
import { VisualizationPanel } from '@/features/visualizers'
import { cn } from '@/lib/utils/cn'
import { useDelayedFlag } from '@/lib/utils/useDelayedFlag'

const KINDS: { kind: ConceptKind; label: string; blurb: string }[] = [
  {
    kind: 'string-reverse',
    label: 'String',
    blurb: 'Reverse a String one character at a time — and see why Strings are immutable.',
  },
  {
    kind: 'array',
    label: 'Array',
    blurb: 'Watch sorting and searching compare, swap and narrow down array cells.',
  },
  {
    kind: 'loops',
    label: 'Loops',
    blurb: 'Follow init, condition and update through every iteration.',
  },
]

const isKind = (k: string | undefined): k is ConceptKind => KINDS.some((x) => x.kind === k)

/** Phase 2 concept visualizers: form → backend generates a trace → same playback as real code. */
export default function ConceptsPage() {
  const { kind } = useParams()
  if (!isKind(kind)) return <Navigate to="/concepts/string-reverse" replace />
  return <ConceptView key={kind} kind={kind} />
}

function ConceptView({ kind }: { kind: ConceptKind }) {
  const mutation = useConceptTrace()
  const { mutate } = mutation
  const [result, setResult] = useState<ConceptResult | null>(null)
  const currentLine = usePlaybackStore((s) => (s.trace ? s.trace.steps[s.index]?.line : undefined))
  const waking = useDelayedFlag(mutation.isPending)
  const info = KINDS.find((k) => k.kind === kind)!

  const run = (request: ConceptRequest) =>
    mutate(request, {
      onSuccess: (r) => {
        setResult(r)
        usePlaybackStore.getState().load(r.trace)
      },
    })

  // Open each concept with a ready-made example; leave no trace behind for the Playground.
  useEffect(() => {
    usePlaybackStore.getState().clear()
    mutate(DEFAULT_REQUEST[kind], {
      onSuccess: (r) => {
        setResult(r)
        usePlaybackStore.getState().load(r.trace)
      },
    })
    return () => usePlaybackStore.getState().clear()
  }, [kind, mutate])

  return (
    <div className="mx-auto flex h-full max-w-7xl flex-col gap-4 overflow-auto p-4 lg:flex-row lg:overflow-hidden">
      <aside className="flex flex-col gap-4 lg:w-96 lg:shrink-0 lg:overflow-auto">
        <div>
          <h1 className="font-brand text-2xl font-bold">Concepts</h1>
          <nav className="mt-3 flex gap-1 rounded-lg bg-surface-2 p-1" aria-label="Concept">
            {KINDS.map((k) => (
              <NavLink
                key={k.kind}
                to={`/concepts/${k.kind}`}
                className={({ isActive }) =>
                  cn(
                    'flex-1 rounded-md px-3 py-1.5 text-center text-sm font-medium',
                    isActive ? 'bg-surface text-fg shadow-sm' : 'text-muted hover:text-fg',
                  )
                }
              >
                {k.label}
              </NavLink>
            ))}
          </nav>
          <p className="mt-3 text-sm text-muted">{info.blurb}</p>
        </div>

        <ConceptForm
          kind={kind}
          pending={mutation.isPending}
          error={mutation.error}
          onSubmit={run}
        />

        {waking && (
          <p className="text-sm text-muted" role="status">
            Waking up server… free hosting can take ~30 s on the first request.
          </p>
        )}

        {result && (
          <div className="space-y-2">
            <h2 className="font-medium">{result.title}</h2>
            <CodeView code={result.code} currentLine={currentLine} />
          </div>
        )}
      </aside>

      <section className="flex min-h-[520px] flex-1 flex-col overflow-hidden rounded-lg border">
        <div className="min-h-0 flex-1">
          <VisualizationPanel
            source={result?.code}
            empty={
              <div className="flex h-full items-center justify-center gap-2 text-sm text-muted">
                {mutation.isPending ? (
                  <>
                    <Loader2 size={16} className="animate-spin" aria-hidden="true" /> Generating
                    trace…
                  </>
                ) : (
                  'Fill in the form and press Visualize.'
                )}
              </div>
            }
          />
        </div>
        <div className="h-36 shrink-0 border-t">
          <ConsolePanel runs={false} />
        </div>
      </section>
    </div>
  )
}
