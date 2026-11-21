import { motion } from 'framer-motion'

/** Index label drawn under a cell (e.g. `i`, `j`, `mid`). Slides between cells via layoutId. */
export function Pointer({ label, layoutId }: { label: string; layoutId: string }) {
  return (
    <motion.div
      layoutId={layoutId}
      transition={{ type: 'spring', stiffness: 500, damping: 35 }}
      className="flex flex-col items-center leading-none text-accent"
      data-testid={`pointer-${label}`}
    >
      <span aria-hidden="true">▲</span>
      <span className="font-mono text-[11px] font-semibold">{label}</span>
    </motion.div>
  )
}
