import { cn } from '@/lib/utils/cn'

interface LogoProps {
  showWordmark?: boolean
  className?: string
}

/** Brackets + play triangle mark, blue primary, purple motion. */
export function Logo({ showWordmark = true, className }: LogoProps) {
  return (
    <span className={cn('flex items-center gap-2', className)}>
      <svg viewBox="0 0 64 64" width="28" height="28" fill="none" aria-hidden="true">
        <path
          d="M20 18 8 32l12 14"
          stroke="var(--jm-primary)"
          strokeWidth="5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M44 18 56 32 44 46"
          stroke="var(--jm-primary)"
          strokeWidth="5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path d="M27 22v20l15-10z" fill="var(--jm-accent)" />
      </svg>
      {showWordmark && (
        <span className="font-brand text-lg font-bold tracking-tight">
          Jav<span className="text-primary">Motion</span>
        </span>
      )}
    </span>
  )
}
