import { useState, type FormEvent, type ReactNode } from 'react'
import { Play } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { ApiError } from '@/lib/api/errors'
import {
  LIMITS,
  type ArrayOperation,
  type ConceptKind,
  type ConceptRequest,
  type LoopType,
} from './conceptApi'
import { describeError } from './describeError'
import { parseInts } from './parseInts'
import { DEFAULT_REQUEST } from './presets'

interface ConceptFormProps {
  kind: ConceptKind
  pending: boolean
  error: unknown
  onSubmit: (request: ConceptRequest) => void
}

/** One form per concept. Server validation is the source of truth; the client only parses. */
export function ConceptForm({ kind, pending, error, onSubmit }: ConceptFormProps) {
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}
  const formError = error && Object.keys(fieldErrors).length === 0 ? describeError(error) : null

  const Form = { 'string-reverse': StringForm, array: ArrayForm, loops: LoopsForm }[kind]
  return (
    <Form
      key={kind}
      pending={pending}
      fieldErrors={fieldErrors}
      formError={formError}
      onSubmit={onSubmit}
    />
  )
}

interface FormProps {
  pending: boolean
  fieldErrors: Record<string, string>
  formError: string | null
  onSubmit: (request: ConceptRequest) => void
}

function StringForm({ pending, fieldErrors, formError, onSubmit }: FormProps) {
  const [input, setInput] = useState(DEFAULT_REQUEST['string-reverse'].body.input)

  return (
    <FormShell
      pending={pending}
      formError={formError}
      onSubmit={() => onSubmit({ kind: 'string-reverse', body: { input } })}
    >
      <Field label="Text to reverse" error={fieldErrors.input}>
        <input
          className={inputClass}
          value={input}
          maxLength={LIMITS.stringMax}
          onChange={(e) => setInput(e.target.value)}
          required
        />
      </Field>
    </FormShell>
  )
}

const OPERATIONS: { value: ArrayOperation; label: string }[] = [
  { value: 'BUBBLE_SORT', label: 'Bubble sort' },
  { value: 'SELECTION_SORT', label: 'Selection sort' },
  { value: 'LINEAR_SEARCH', label: 'Linear search' },
  { value: 'BINARY_SEARCH', label: 'Binary search (sorted input)' },
]

function ArrayForm({ pending, fieldErrors, formError, onSubmit }: FormProps) {
  const defaults = DEFAULT_REQUEST.array.body
  const [operation, setOperation] = useState<ArrayOperation>(defaults.operation)
  const [values, setValues] = useState(defaults.values.join(', '))
  const [target, setTarget] = useState('4')
  const [parseError, setParseError] = useState<string | null>(null)
  const isSearch = operation === 'LINEAR_SEARCH' || operation === 'BINARY_SEARCH'

  const submit = () => {
    const parsed = parseInts(values)
    if (!parsed) return setParseError('Enter whole numbers separated by commas.')
    if (isSearch && !/^-?\d+$/.test(target.trim()))
      return setParseError('Enter a whole-number target.')
    setParseError(null)
    onSubmit({
      kind: 'array',
      body: { operation, values: parsed, ...(isSearch ? { target: Number(target) } : {}) },
    })
  }

  return (
    <FormShell pending={pending} formError={parseError ?? formError} onSubmit={submit}>
      <Field label="Operation">
        <select
          className={inputClass}
          value={operation}
          onChange={(e) => setOperation(e.target.value as ArrayOperation)}
        >
          {OPERATIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </Field>
      <Field
        label={`Values (up to ${LIMITS.arrayMax}, ${LIMITS.valueMin}..${LIMITS.valueMax})`}
        error={fieldErrors.values ?? firstMatching(fieldErrors, 'values[')}
      >
        <input className={inputClass} value={values} onChange={(e) => setValues(e.target.value)} />
      </Field>
      {isSearch && (
        <Field label="Target" error={fieldErrors.target}>
          <input
            className={inputClass}
            inputMode="numeric"
            value={target}
            onChange={(e) => setTarget(e.target.value)}
          />
        </Field>
      )}
    </FormShell>
  )
}

const LOOP_TYPES: { value: LoopType; label: string }[] = [
  { value: 'FOR', label: 'for' },
  { value: 'WHILE', label: 'while' },
  { value: 'DO_WHILE', label: 'do-while' },
  { value: 'NESTED', label: 'nested for' },
]

function LoopsForm({ pending, fieldErrors, formError, onSubmit }: FormProps) {
  const defaults = DEFAULT_REQUEST.loops.body
  const [type, setType] = useState<LoopType>(defaults.type)
  const [start, setStart] = useState(String(defaults.start))
  const [end, setEnd] = useState(String(defaults.end))
  const [step, setStep] = useState('1')

  return (
    <FormShell
      pending={pending}
      formError={formError}
      onSubmit={() =>
        onSubmit({
          kind: 'loops',
          body: { type, start: Number(start), end: Number(end), step: Number(step) },
        })
      }
    >
      <Field label="Loop type">
        <select
          className={inputClass}
          value={type}
          onChange={(e) => setType(e.target.value as LoopType)}
        >
          {LOOP_TYPES.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </select>
      </Field>
      <div className="grid grid-cols-3 gap-3">
        <Field label="Start" error={fieldErrors.start}>
          <NumberInput
            value={start}
            onChange={setStart}
            min={LIMITS.loopMin}
            max={LIMITS.loopMax}
          />
        </Field>
        <Field label="End" error={fieldErrors.end}>
          <NumberInput value={end} onChange={setEnd} min={LIMITS.loopMin} max={LIMITS.loopMax} />
        </Field>
        <Field label="Step" error={fieldErrors.step}>
          <NumberInput value={step} onChange={setStep} min={1} max={LIMITS.stepMax} />
        </Field>
      </div>
    </FormShell>
  )
}

// ---------------------------------------------------------------- shared bits

const inputClass =
  'h-9 w-full rounded-md border bg-surface-2 px-2 text-sm text-fg focus-visible:outline-2 focus-visible:outline-primary'

function FormShell({
  children,
  pending,
  formError,
  onSubmit,
}: {
  children: ReactNode
  pending: boolean
  formError: string | null
  onSubmit: () => void
}) {
  const handle = (e: FormEvent) => {
    e.preventDefault()
    onSubmit()
  }
  return (
    <form onSubmit={handle} className="space-y-3" noValidate>
      {children}
      {formError && (
        <p role="alert" className="text-sm text-error">
          {formError}
        </p>
      )}
      <Button type="submit" variant="accent" size="sm" disabled={pending}>
        <Play size={16} /> {pending ? 'Generating…' : 'Visualize'}
      </Button>
    </form>
  )
}

function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="block space-y-1 text-sm">
      <span className="text-muted">{label}</span>
      {children}
      {error && <span className="block text-xs text-error">{error}</span>}
    </label>
  )
}

function NumberInput(props: {
  value: string
  onChange: (v: string) => void
  min: number
  max: number
}) {
  return (
    <input
      type="number"
      className={inputClass}
      value={props.value}
      min={props.min}
      max={props.max}
      onChange={(e) => props.onChange(e.target.value)}
    />
  )
}

function firstMatching(errors: Record<string, string>, prefix: string) {
  return Object.entries(errors).find(([k]) => k.startsWith(prefix))?.[1]
}
