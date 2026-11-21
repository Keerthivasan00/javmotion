// Generates fake trace fixtures for JavMotion frontend Step 2.
// Usage (from javmotion-frontend): node scripts/gen-fixtures.mjs src/test/fixtures/traces
// Then copy the JSON files to javmotion-backend/javmotion-common/src/test/resources/trace-samples/
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const outDir = process.argv[2]
mkdirSync(outDir, { recursive: true })

const int = (v) => ({ kind: 'PRIMITIVE', type: 'int', value: v })
const ref = (r) => ({ kind: 'REF', ref: r })
const str = (v) => ({ kind: 'STRING', type: 'java.lang.String', value: v })
const frame = (className, line, locals) => ({ id: 'f1', method: 'main', className, line, locals })

function build(source, className, raw) {
  return {
    schemaVersion: 1,
    source,
    language: 'java',
    truncated: false,
    steps: raw.map((s, index) => {
      const step = {
        index,
        event: s.event ?? 'LINE',
        line: s.line,
        stack: s.event === 'END' ? [] : [frame(className, s.line, s.locals)],
        heap: s.heap,
        stdout: s.stdout,
      }
      if (s.highlights) step.highlights = s.highlights
      return step
    }),
  }
}

// ---------- for-loop (JDI-style, no highlights) ----------
{
  const argsHeap = { h0: { kind: 'ARRAY', type: 'java.lang.String[]', elements: [] } }
  const steps = []
  let stdout = ''
  const push = (line, locals, event) =>
    steps.push({ line, locals: { args: ref('h0'), ...locals }, heap: argsHeap, stdout, event })

  push(3, {}, 'CALL')
  push(4, { sum: int(0) })
  let sum = 0
  for (let i = 1; i <= 3; i++) {
    push(5, { sum: int(sum), i: int(i) })
    sum += i
    push(6, { sum: int(sum), i: int(i) })
    stdout += `sum = ${sum}\n`
    push(4, { sum: int(sum), i: int(i) })
  }
  push(8, { sum: int(sum) })
  stdout += 'done\n'
  push(9, { sum: int(sum) })
  steps.push({ line: 9, locals: {}, heap: {}, stdout, event: 'END' })
  writeFileSync(join(outDir, 'for-loop.json'), JSON.stringify(build('JDI', 'Main', steps), null, 2) + '\n')
}

// ---------- array-bubble-sort (CONCEPT, highlights) ----------
{
  const arr = [5, 1, 4, 2]
  const n = arr.length
  const steps = []
  const heap = () => ({ h1: { kind: 'ARRAY', type: 'int[]', elements: arr.map(int) } })
  const push = (line, locals, highlights, stdout = '') =>
    steps.push({ line, locals, heap: heap(), stdout, highlights })
  const base = { arr: ref('h1') }
  const ptr = (index, label) => ({ ref: 'h1', index, role: 'pointer', label })

  push(4, base)
  push(5, { ...base, n: int(n) }) // outer loop header (first check)
  const sortedFrom = []
  for (let i = 0; i < n - 1; i++) {
    const done = () => sortedFrom.map((k) => ({ ref: 'h1', index: k, role: 'found' }))
    let lastJ = 0
    for (let j = 0; j < n - 1 - i; j++) {
      lastJ = j
      const loc = { ...base, n: int(n), i: int(i), j: int(j) }
      push(6, loc, [ptr(j, 'j'), ...done()]) // inner loop header
      push(7, loc, [
        { ref: 'h1', index: j, role: 'compare' },
        { ref: 'h1', index: j + 1, role: 'compare' },
        ptr(j, 'j'),
        ...done(),
      ])
      if (arr[j] > arr[j + 1]) {
        ;[arr[j], arr[j + 1]] = [arr[j + 1], arr[j]]
        push(9, loc, [
          { ref: 'h1', index: j, role: 'swap' },
          { ref: 'h1', index: j + 1, role: 'swap' },
          ptr(j, 'j'),
          ...done(),
        ])
      }
    }
    sortedFrom.push(n - 1 - i)
    // Inner header re-checked: condition false (JDI shows j before the update runs).
    push(6, { ...base, n: int(n), i: int(i), j: int(lastJ) }, done())
    // Back to the outer header.
    push(5, { ...base, n: int(n), i: int(i) }, done())
  }
  sortedFrom.push(0)
  const all = arr.map((_, k) => ({ ref: 'h1', index: k, role: 'found' }))
  push(14, { ...base, n: int(n) }, all)
  const out = `[${arr.join(', ')}]\n`
  steps.push({ line: 15, locals: {}, heap: heap(), stdout: out, highlights: all, event: 'END' })
  writeFileSync(
    join(outDir, 'array-bubble-sort.json'),
    JSON.stringify(build('CONCEPT', 'BubbleSort', steps), null, 2) + '\n',
  )
}

// ---------- string-reverse (CONCEPT, new String objects each iteration) ----------
{
  const s = 'code'
  const steps = []
  const heap = { h1: str(s) }
  let stdout = ''
  const snap = () => structuredClone(heap)
  const push = (line, locals, highlights, event) =>
    steps.push({ line, locals, heap: snap(), stdout, highlights, event })

  push(3, {}, undefined, 'CALL')
  let rev = ''
  let revId = 'h2'
  heap[revId] = str(rev)
  push(4, { s: ref('h1') })
  let next = 3
  for (let i = s.length - 1; i >= 0; i--) {
    const loc = () => ({ s: ref('h1'), rev: ref(revId), i: int(i) })
    push(5, loc(), [{ ref: 'h1', index: i, role: 'pointer', label: 'i' }])
    push(6, loc(), [
      { ref: 'h1', index: i, role: 'active', label: 'i' },
    ])
    rev = rev + s[i]
    delete heap[revId] // old string becomes garbage (unreachable)
    revId = `h${next++}`
    heap[revId] = str(rev)
  }
  // Header re-checked after the last iteration: condition false, loop exits.
  push(5, { s: ref('h1'), rev: ref(revId), i: int(0) })
  push(8, { s: ref('h1'), rev: ref(revId) }, [{ ref: revId, role: 'found' }])
  stdout += `${rev}\n`
  steps.push({ line: 9, locals: {}, heap: snap(), stdout, event: 'END' })
  writeFileSync(
    join(outDir, 'string-reverse.json'),
    JSON.stringify(build('CONCEPT', 'StringReverse', steps), null, 2) + '\n',
  )
}

console.log('fixtures written to', outDir)
