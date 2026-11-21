import { beforeEach, describe, expect, it } from 'vitest'
import { useEditorStore } from './editorStore'
import { DEFAULT_TEMPLATE } from './templates'

describe('editorStore', () => {
  beforeEach(() => useEditorStore.getState().reset())

  it('marks source dirty on edit', () => {
    useEditorStore.getState().setSource('class A {}')
    expect(useEditorStore.getState()).toMatchObject({ source: 'class A {}', dirty: true })
  })

  it('reset restores the default template', () => {
    useEditorStore.getState().setSource('x')
    useEditorStore.getState().reset()
    expect(useEditorStore.getState()).toMatchObject({ source: DEFAULT_TEMPLATE.code, dirty: false })
  })
})
