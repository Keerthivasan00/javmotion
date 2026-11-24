/** A problem to underline, 1-based like javac. */
export interface EditorDiagnostic {
  line: number
  column: number
  message: string
}

/** javac positions → Monaco markers, clamped to the document. */
export function toMarkers(
  diagnostics: EditorDiagnostic[],
  lineCount: number,
  monaco: { MarkerSeverity: { Error: number } },
) {
  return diagnostics.map((d) => {
    const line = Math.min(Math.max(1, d.line), Math.max(1, lineCount))
    const column = Math.max(1, d.column)
    return {
      severity: monaco.MarkerSeverity.Error,
      message: d.message,
      startLineNumber: line,
      startColumn: column,
      endLineNumber: line,
      // Underline one token's worth; Monaco extends to the word under the column.
      endColumn: column + 1,
    }
  })
}
