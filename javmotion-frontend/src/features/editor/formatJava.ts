/**
 * Minimal Java re-indenter for the Format button (Phase 1).
 * Re-indents by brace depth, trims trailing whitespace, collapses runs of blank lines.
 * Ignores braces inside strings, char literals and comments.
 * Not a real formatter — a backend google-java-format endpoint can replace it later.
 */
export function formatJava(source: string, indent = '    '): string {
  const lines = source.replace(/\r\n?/g, '\n').split('\n')
  const out: string[] = []
  let depth = 0
  let inBlockComment = false
  let inTextBlock = false
  let blankRun = 0

  for (const raw of lines) {
    const line = raw.trim()

    // Keep text block content (""" ... """) untouched apart from trailing spaces.
    if (inTextBlock) {
      out.push(raw.replace(/\s+$/, ''))
      if (line.includes('"""')) inTextBlock = false
      continue
    }

    if (line === '') {
      blankRun++
      if (blankRun <= 1 && out.length > 0) out.push('')
      continue
    }
    blankRun = 0

    const { opens, closes, leadingCloses, endsInBlockComment, startsTextBlock } = scan(
      line,
      inBlockComment,
    )

    const lineDepth = Math.max(0, depth - leadingCloses)
    // Continuation lines inside a block comment get one extra space (" * ...").
    const prefix = inBlockComment && line.startsWith('*') ? ' ' : ''
    out.push(indent.repeat(lineDepth) + prefix + line)

    depth = Math.max(0, depth + opens - closes)
    inBlockComment = endsInBlockComment
    inTextBlock = startsTextBlock
  }

  while (out.length > 0 && out[out.length - 1] === '') out.pop()
  return out.join('\n') + '\n'
}

interface ScanResult {
  opens: number
  closes: number
  /** Closing braces before any other code on the line (dedent this line). */
  leadingCloses: number
  endsInBlockComment: boolean
  startsTextBlock: boolean
}

function scan(line: string, startInBlockComment: boolean): ScanResult {
  let opens = 0
  let closes = 0
  let leadingCloses = 0
  let seenCode = false
  let inBlock = startInBlockComment
  let i = 0

  while (i < line.length) {
    const c = line[i]
    const next = line[i + 1]

    if (inBlock) {
      if (c === '*' && next === '/') {
        inBlock = false
        i += 2
      } else i++
      continue
    }
    if (c === '/' && next === '/') break
    if (c === '/' && next === '*') {
      inBlock = true
      i += 2
      continue
    }
    if (line.startsWith('"""', i)) {
      const end = line.indexOf('"""', i + 3)
      if (end === -1) {
        return { opens, closes, leadingCloses, endsInBlockComment: false, startsTextBlock: true }
      }
      seenCode = true
      i = end + 3
      continue
    }
    if (c === '"' || c === "'") {
      seenCode = true
      i = skipQuoted(line, i, c)
      continue
    }
    if (c === '{') {
      opens++
      seenCode = true
    } else if (c === '}') {
      closes++
      if (!seenCode) leadingCloses++
    } else if (!/\s/.test(c)) {
      seenCode = true
    }
    i++
  }

  return { opens, closes, leadingCloses, endsInBlockComment: inBlock, startsTextBlock: false }
}

function skipQuoted(line: string, start: number, quote: string): number {
  let i = start + 1
  while (i < line.length) {
    if (line[i] === '\\') i += 2
    else if (line[i] === quote) return i + 1
    else i++
  }
  return i
}
