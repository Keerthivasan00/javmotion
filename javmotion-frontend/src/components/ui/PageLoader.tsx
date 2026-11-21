import { Loader2 } from 'lucide-react'

export function PageLoader() {
  return (
    <div className="flex h-full items-center justify-center text-muted">
      <Loader2 className="animate-spin" size={24} aria-label="Loading" />
    </div>
  )
}
