import { useParams } from 'react-router-dom'

/** TODO (Step 5): concept forms (string-reverse, array, loops) → fetch trace → playback. */
export default function ConceptsPage() {
  const { kind } = useParams()

  return (
    <div className="mx-auto max-w-3xl space-y-2 p-6">
      <h1 className="font-brand text-2xl font-bold">Concepts</h1>
      <p className="text-muted">
        {kind ? `Visualizer: ${kind}` : 'String, array and loop visualizers are coming in Step 5.'}
      </p>
    </div>
  )
}
