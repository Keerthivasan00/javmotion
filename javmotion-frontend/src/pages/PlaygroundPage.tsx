import { useState } from 'react'
import {
  Group,
  Panel,
  Separator,
  useDefaultLayout,
  type LayoutStorage,
} from 'react-resizable-panels'
import { ConsolePanel } from '@/features/console'
import { CodeEditor, EditorToolbar } from '@/features/editor'
import { VisualizationPanel } from '@/features/visualizers'
import { useMediaQuery } from '@/lib/utils/useMediaQuery'
import { cn } from '@/lib/utils/cn'
import { storage } from '@/lib/utils/storage'

/** Safe localStorage adapter so pane sizes survive reloads (never throws). */
const layoutStorage: LayoutStorage = {
  getItem: (key) => storage.get(key),
  setItem: (key, value) => storage.set(key, value),
}

const handle = 'bg-border transition-colors hover:bg-primary data-[separator]:outline-none'

export default function PlaygroundPage() {
  const isDesktop = useMediaQuery('(min-width: 768px)')

  return (
    <div className="flex h-full flex-col">
      <EditorToolbar />
      <div className="min-h-0 flex-1">{isDesktop ? <DesktopLayout /> : <MobileLayout />}</div>
    </div>
  )
}

function DesktopLayout() {
  const outer = useDefaultLayout({ id: 'jm-playground-rows', storage: layoutStorage })
  const inner = useDefaultLayout({ id: 'jm-playground-cols', storage: layoutStorage })

  return (
    <Group
      orientation="vertical"
      defaultLayout={outer.defaultLayout}
      onLayoutChanged={outer.onLayoutChanged}
    >
      <Panel id="main" defaultSize="72" minSize="30">
        <Group
          orientation="horizontal"
          defaultLayout={inner.defaultLayout}
          onLayoutChanged={inner.onLayoutChanged}
        >
          <Panel id="editor" defaultSize="55" minSize="25">
            <CodeEditor />
          </Panel>
          <Separator className={cn('w-1', handle)} />
          <Panel id="visualization" defaultSize="45" minSize="25">
            <VisualizationPanel />
          </Panel>
        </Group>
      </Panel>
      <Separator className={cn('h-1', handle)} />
      <Panel id="console" defaultSize="28" minSize="10">
        <ConsolePanel />
      </Panel>
    </Group>
  )
}

const MOBILE_TABS = ['Code', 'Visualize', 'Console'] as const

function MobileLayout() {
  const [tab, setTab] = useState<(typeof MOBILE_TABS)[number]>('Code')

  return (
    <div className="flex h-full flex-col">
      <div className="flex shrink-0 border-b bg-surface" role="tablist">
        {MOBILE_TABS.map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => setTab(t)}
            className={cn(
              'flex-1 py-2 text-sm font-medium',
              tab === t ? 'border-b-2 border-primary text-fg' : 'text-muted',
            )}
          >
            {t}
          </button>
        ))}
      </div>
      <div className="min-h-0 flex-1">
        {tab === 'Code' && <CodeEditor />}
        {tab === 'Visualize' && <VisualizationPanel />}
        {tab === 'Console' && <ConsolePanel />}
      </div>
    </div>
  )
}
