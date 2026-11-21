import { cn } from '@/lib/utils/cn'
import { usePing } from './systemApi'

/** Small dot in the header showing whether the backend answers /system/ping. */
export function ServerStatus() {
  const { data, isPending, isError } = usePing()

  const state = isPending ? 'checking' : isError ? 'offline' : 'online'
  const label = {
    checking: 'Checking server…',
    offline: 'Server offline',
    online: `Server online (v${data?.version})`,
  }[state]

  return (
    <span className="flex items-center gap-2 text-xs text-muted" title={label}>
      <span
        className={cn(
          'h-2 w-2 rounded-full',
          state === 'online' && 'bg-success',
          state === 'offline' && 'bg-error',
          state === 'checking' && 'animate-pulse bg-warning',
        )}
      />
      <span className="hidden md:inline">{label}</span>
    </span>
  )
}
