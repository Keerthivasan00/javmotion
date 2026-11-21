import { Clapperboard } from 'lucide-react'
import { useEditorStore } from '@/features/editor'
import { usePlaybackStore } from '@/features/playback'
import { useDelayedFlag } from '@/lib/utils/useDelayedFlag'
import { useConceptTrace } from './conceptApi'
import { describeError } from './describeError'
import { DEMOS } from './presets'

/**
 * Playground empty state: loads a ready-made program + its trace from the concept API
 * (until Visualize works on your own code in Step 7).
 */
export function DemoPicker() {
  const mutation = useConceptTrace()
  const waking = useDelayedFlag(mutation.isPending)

  const load = (id: string) => {
    const demo = DEMOS.find((d) => d.id === id)
    if (!demo) return
    mutation.mutate(demo.request, {
      onSuccess: (result) => {
        useEditorStore.getState().loadTemplate(result.code)
        usePlaybackStore.getState().load(result.trace)
      },
    })
  }

  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 p-6 text-center">
      <Clapperboard size={40} className="text-accent" aria-hidden="true" />
      <p className="font-medium">Click Visualize to see your code in motion</p>
      <p className="max-w-xs text-sm text-muted">
        Variables, arrays, loops and memory will animate here step by step.
      </p>
      <label className="mt-2 flex items-center gap-2 text-sm text-muted">
        Try a demo
        <select
          className="h-8 rounded-md border bg-surface-2 px-2 text-sm text-fg"
          defaultValue=""
          disabled={mutation.isPending}
          onChange={(e) => load(e.target.value)}
        >
          <option value="" disabled>
            Choose…
          </option>
          {DEMOS.map((d) => (
            <option key={d.id} value={d.id}>
              {d.title}
            </option>
          ))}
        </select>
      </label>
      {mutation.isPending && (
        <p className="text-sm text-muted" role="status">
          {waking ? 'Waking up server… (free hosting can take ~30 s)' : 'Loading…'}
        </p>
      )}
      {mutation.isError && (
        <p className="text-sm text-error" role="alert">
          {describeError(mutation.error)}
        </p>
      )}
    </div>
  )
}
