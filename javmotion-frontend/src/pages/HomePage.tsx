import { ArrowRight, Code2, Eye, Lightbulb, Play } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Logo } from '@/components/brand/Logo'

const STEPS = [
  { icon: Code2, label: 'Write' },
  { icon: Play, label: 'Execute' },
  { icon: Eye, label: 'Visualize' },
  { icon: Lightbulb, label: 'Understand' },
]

export function HomePage() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-8 overflow-auto px-4 py-12 text-center">
      <Logo showWordmark={false} className="scale-[2.5]" />
      <div className="space-y-3">
        <h1 className="font-brand text-4xl font-bold tracking-tight sm:text-5xl">
          See Code in <span className="text-primary">Motion</span>.
        </h1>
        <p className="mx-auto max-w-xl text-muted">
          Write Java, run it, and watch every line, variable and loop come alive step by step.
        </p>
      </div>

      <ol className="flex flex-wrap justify-center gap-3">
        {STEPS.map(({ icon: Icon, label }) => (
          <li
            key={label}
            className="flex items-center gap-2 rounded-full border bg-surface px-4 py-2 text-sm"
          >
            <Icon size={16} className="text-accent" aria-hidden="true" />
            {label}
          </li>
        ))}
      </ol>

      <Link
        to="/playground"
        className="inline-flex h-11 items-center gap-2 rounded-md bg-primary px-6 font-medium text-white hover:bg-primary-hover"
      >
        Open Playground <ArrowRight size={18} />
      </Link>
    </div>
  )
}
