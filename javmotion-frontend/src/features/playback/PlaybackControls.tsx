import { ChevronLeft, ChevronRight, Pause, Play, SkipBack, SkipForward } from 'lucide-react'
import { IconButton } from '@/components/ui/IconButton'
import { SPEEDS, usePlaybackStore, type Speed } from './playbackStore'
import { selectStepCount } from './selectors'

export function PlaybackControls() {
  const index = usePlaybackStore((s) => s.index)
  const playing = usePlaybackStore((s) => s.playing)
  const speed = usePlaybackStore((s) => s.speed)
  const count = usePlaybackStore(selectStepCount)
  const line = usePlaybackStore((s) => s.trace?.steps[s.index]?.line)
  const { reset, prev, togglePlay, next, continueToBreakpoint, seek, setSpeed, pause } =
    usePlaybackStore.getState()

  if (count === 0) return null
  const atStart = index === 0
  const atEnd = index >= count - 1

  return (
    <div
      className="flex shrink-0 flex-wrap items-center gap-x-1 gap-y-2 border-t bg-surface px-2 py-2"
      role="toolbar"
      aria-label="Playback controls"
    >
      <IconButton label="Restart" onClick={reset} disabled={atStart}>
        <SkipBack size={18} />
      </IconButton>
      <IconButton label="Previous step (Shift+F10)" onClick={prev} disabled={atStart}>
        <ChevronLeft size={20} />
      </IconButton>
      <IconButton
        label={playing ? 'Pause' : 'Play'}
        onClick={togglePlay}
        className="bg-accent/15 text-accent hover:bg-accent/25 hover:text-accent"
      >
        {playing ? <Pause size={18} /> : <Play size={18} />}
      </IconButton>
      <IconButton
        label="Next step (F10)"
        onClick={() => {
          pause()
          next()
        }}
        disabled={atEnd}
      >
        <ChevronRight size={20} />
      </IconButton>
      <IconButton
        label="Continue to breakpoint (F5)"
        onClick={continueToBreakpoint}
        disabled={atEnd}
      >
        <SkipForward size={18} />
      </IconButton>

      <input
        type="range"
        min={0}
        max={count - 1}
        value={index}
        onChange={(e) => {
          pause()
          seek(Number(e.target.value))
        }}
        aria-label="Step"
        aria-valuetext={`Step ${index + 1} of ${count}`}
        className="mx-2 min-w-24 flex-1 accent-[var(--jm-accent)]"
      />

      <span className="font-mono text-xs whitespace-nowrap text-muted" aria-live="polite">
        Step {index + 1}/{count}
        {line !== undefined && <span className="hidden sm:inline"> · line {line}</span>}
      </span>

      <select
        value={speed}
        onChange={(e) => setSpeed(Number(e.target.value) as Speed)}
        aria-label="Playback speed"
        className="ml-1 h-8 rounded-md border bg-surface-2 px-1 text-xs text-fg"
      >
        {SPEEDS.map((s) => (
          <option key={s} value={s}>
            {s}×
          </option>
        ))}
      </select>
    </div>
  )
}
