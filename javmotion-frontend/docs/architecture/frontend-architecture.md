# JavMotion Frontend Architecture

> Scope: `javmotion-frontend/` — React playground, editor, playback engine, visualizers, auth, theming, deployment.
> Companion doc: [backend-architecture.md](backend-architecture.md) (owns the trace format, §6).

---

## 1. Overview

Single-page app built with **React + Vite + TypeScript**, deployed as static files to **GitHub Pages**. It talks to the Spring Boot API over HTTPS/JSON.

Core idea: the backend returns a **trace** (array of snapshots). The frontend is a **trace player**: a playback engine picks the current step, and every panel (editor highlight, variables, memory, array/string/loop visualizers, console) renders from that one step.

```
                      ┌──────────────────────────── Playground ───────────────────────────┐
                      │                                                                   │
 Monaco Editor ──run──▶ executionApi ──poll──▶ Trace ──▶ playbackStore ──currentStep──┐   │
      ▲                                                     ▲   (index, playing,       │   │
      │ line highlight / breakpoints / error markers        │    speed, breakpoints)   │   │
      └─────────────────────────────────────────────────────┼──────────────────────────┤   │
                                                PlaybackControls                       ▼   │
                                                                   Visualization Panel     │
                                                                   ├ VariableInspector     │
                                                                   ├ ArrayVisualizer       │
                                                                   ├ StringVisualizer      │
                                                                   ├ LoopVisualizer        │
                                                                   ├ MemoryVisualizer      │
                                                                   └ CallStack             │
                                                                   Console Panel           │
                      └───────────────────────────────────────────────────────────────────┘
```

---

## 2. Tech Stack

| Concern | Choice | Why |
|---|---|---|
| Build | Vite | Fast dev server, simple static build for Pages |
| Language | **TypeScript** (strict) | Trace schema is complex; types catch contract drift |
| UI | React 18 | |
| Styling | Tailwind CSS + CSS variables | Brand tokens, dark/light switching |
| Editor | Monaco via `@monaco-editor/react` | VS Code editing, decorations, markers |
| Animation | Framer Motion | Layout animations for array swaps, stack frames, pointers |
| Icons | Lucide React | |
| Routing | React Router v6 | |
| Server state | TanStack Query | Caching, polling, retries for executions/programs |
| Client state | Zustand | Playback + editor state shared across panels without prop drilling |
| Forms | React Hook Form + Zod | Login/register/program forms; Zod also validates trace payloads |
| Layout | `react-resizable-panels` | Draggable 3-pane playground |
| HTTP | `fetch` wrapper (or Axios) | Central auth header + refresh handling |
| Tests | Vitest + React Testing Library, Playwright (E2E) | |
| Lint/format | ESLint + Prettier | |

---

## 3. Folder Structure (feature-based)

```
javmotion-frontend/
├── index.html
├── vite.config.ts                 # base: '/javmotion/' for GitHub Pages
├── tailwind.config.ts
├── tsconfig.json
├── .env.development               # VITE_API_BASE_URL=http://localhost:8080/api/v1
├── .env.production                # VITE_API_BASE_URL=https://<api>.onrender.com/api/v1
├── public/
│   ├── favicon.svg
│   └── 404.html                   # SPA fallback for GitHub Pages
└── src/
    ├── main.tsx
    ├── app/
    │   ├── App.tsx
    │   ├── router.tsx             # route table, lazy pages
    │   ├── providers.tsx          # QueryClient, Theme, Auth
    │   └── layouts/
    │       ├── AppLayout.tsx      # top nav + outlet
    │       └── PlaygroundLayout.tsx
    ├── pages/
    │   ├── HomePage.tsx
    │   ├── PlaygroundPage.tsx
    │   ├── ConceptsPage.tsx       # Phase 2 concept visualizers
    │   ├── LearnPage.tsx / LessonPage.tsx
    │   ├── ChallengesPage.tsx / ChallengePage.tsx
    │   ├── ProgramsPage.tsx
    │   ├── ProfilePage.tsx
    │   ├── LoginPage.tsx / RegisterPage.tsx
    │   └── NotFoundPage.tsx
    ├── features/
    │   ├── editor/
    │   │   ├── CodeEditor.tsx            # Monaco wrapper
    │   │   ├── EditorToolbar.tsx         # Run, Visualize, Reset, Format, theme
    │   │   ├── useEditorDecorations.ts   # current line, breakpoints, error markers
    │   │   ├── editorStore.ts            # source, dirty flag, stdin
    │   │   └── templates.ts              # starter programs
    │   ├── execution/
    │   │   ├── executionApi.ts           # create, status, output, events
    │   │   ├── useRunCode.ts             # submit + poll until terminal status
    │   │   ├── ExecutionStatusBar.tsx
    │   │   └── compileErrors.ts          # map API errors to Monaco markers
    │   ├── playback/
    │   │   ├── playbackStore.ts          # Zustand: trace, index, playing, speed, breakpoints
    │   │   ├── usePlaybackTicker.ts      # timer advancing steps while playing
    │   │   ├── PlaybackControls.tsx      # play/pause/prev/next/reset/speed/scrubber
    │   │   └── selectors.ts              # currentStep, prevStep, changedVariables...
    │   ├── visualizers/
    │   │   ├── VisualizationPanel.tsx    # picks visualizers via registry, tabs
    │   │   ├── registry.ts
    │   │   ├── variables/VariableInspector.tsx
    │   │   ├── array/ArrayVisualizer.tsx
    │   │   ├── string/StringVisualizer.tsx
    │   │   ├── loop/LoopVisualizer.tsx
    │   │   ├── memory/MemoryVisualizer.tsx   # stack frames + heap graph + arrows
    │   │   ├── memory/ReferenceArrows.tsx
    │   │   ├── callstack/CallStack.tsx
    │   │   └── shared/ValueCell.tsx, Pointer.tsx, ChangeFlash.tsx
    │   ├── console/
    │   │   └── ConsolePanel.tsx          # stdout (up to step), stderr, errors, duration
    │   ├── concepts/
    │   │   ├── conceptApi.ts             # /visualizer/* endpoints
    │   │   └── ConceptForm.tsx           # input for string/array/loop demos
    │   ├── auth/
    │   │   ├── authApi.ts
    │   │   ├── authStore.ts              # access token in memory, user
    │   │   ├── RequireAuth.tsx
    │   │   └── LoginForm.tsx / RegisterForm.tsx
    │   ├── programs/
    │   │   ├── programsApi.ts
    │   │   ├── ProgramList.tsx
    │   │   └── SaveProgramDialog.tsx
    │   └── learning/
    │       ├── learningApi.ts
    │       ├── LessonView.tsx            # markdown + "Open in Playground"
    │       └── ChallengeRunner.tsx
    ├── components/
    │   ├── ui/                           # Button, IconButton, Tabs, Dialog, Tooltip, Badge, Spinner, Toast
    │   └── brand/Logo.tsx
    ├── lib/
    │   ├── api/
    │   │   ├── client.ts                 # fetch wrapper: base URL, auth header, 401 → refresh → retry
    │   │   └── errors.ts                 # ApiError type matching backend error shape
    │   ├── trace/
    │   │   ├── types.ts                  # TS mirror of backend trace schema
    │   │   ├── schema.ts                 # Zod schema for runtime validation
    │   │   └── diff.ts                   # step-to-step change detection
    │   ├── theme/ThemeProvider.tsx
    │   └── utils/
    ├── styles/
    │   └── globals.css                   # Tailwind layers + CSS variable tokens
    └── test/
        └── fixtures/traces/*.json        # sample traces shared with backend tests
```

**Rules**
- A feature exports its public pieces via `features/<name>/index.ts`. Pages compose features; features don't import from pages.
- Features may import from `components/`, `lib/`, and other features' public `index.ts` only.
- All API calls live in `*Api.ts` files and are consumed through TanStack Query hooks.

---

## 4. Routing

| Path | Page | Auth |
|---|---|---|
| `/` | Home (hero, tagline "See Code in Motion.") | public |
| `/playground` | Playground (blank or template) | public |
| `/playground/:programId` | Playground with saved program | user |
| `/concepts/:kind` | Concept visualizers (`string-reverse`, `array`, `loops`) | public |
| `/learn`, `/learn/:slug` | Lessons | public |
| `/challenges`, `/challenges/:id` | Challenges | public view, user submit |
| `/programs` | Saved programs | user |
| `/profile` | Profile + history | user |
| `/login`, `/register` | Auth | guest |

Pages are lazy-loaded (`React.lazy`) — Monaco is heavy (~2 MB), so it loads only on playground/challenge routes.

**GitHub Pages note:** Pages has no server-side rewrites. Use `BrowserRouter` with `basename="/javmotion"` plus the `404.html` redirect trick, or switch to `HashRouter` if that proves fragile. Decide once in Phase 1.

---

## 5. Playground Layout

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [Logo] JavMotion    Playground  Concepts  Learn  Challenges      [☾] [User]  │
├──────────────────────────────────────────────────────────────────────────────┤
│ EditorToolbar: [▶ Run] [◉ Visualize] [⟲ Reset] [Format] [Save]   status: ✓ 42ms│
├───────────────────────────────┬──────────────────────────────────────────────┤
│                               │ Visualization Panel                          │
│   Monaco Editor               │  Tabs: Variables | Memory | Array | String   │
│   ● breakpoint gutter         │         | Loop | Call Stack                  │
│   ▶ current line highlight    │                                              │
│                               │  (animated view of current step)             │
│                               ├──────────────────────────────────────────────┤
│                               │ PlaybackControls                             │
│                               │ [⏮] [◀] [▶/⏸] [▶] [⏭]  ───●──────  12/87  1x │
├───────────────────────────────┴──────────────────────────────────────────────┤
│ Console: Output | Errors      stdout up to current step / compile errors     │
└──────────────────────────────────────────────────────────────────────────────┘
```

- Panes resizable; sizes persisted to `localStorage` (wrapped in try/catch).
- In **Run** mode the visualization panel collapses and console expands.
- Mobile (< 768px): stacked tabs — Code | Visualize | Console.

---

## 6. State Management

### 6.1 Where state lives

| State | Store | Reason |
|---|---|---|
| Executions, programs, lessons, user profile | TanStack Query cache | Server state: caching, polling, invalidation |
| Source code, stdin, dirty flag | `editorStore` (Zustand) | Shared by editor, toolbar, save dialog |
| Trace, current index, playing, speed, breakpoints | `playbackStore` (Zustand) | Read by every visualizer; must update at high frequency without re-rendering the whole tree |
| Access token, current user | `authStore` (Zustand, memory only) | Never in localStorage (XSS) |
| Theme, pane sizes, last template | `localStorage` | Per-browser convenience only |

Components subscribe with selectors (`usePlaybackStore(s => s.index)`) so a step change re-renders only what reads it.

### 6.2 Playback store

```ts
interface PlaybackState {
  trace: Trace | null;
  index: number;              // current step
  playing: boolean;
  speed: 0.25 | 0.5 | 1 | 2 | 4;
  breakpoints: Set<number>;   // line numbers

  load(trace: Trace): void;   // resets index=0, playing=false
  play(): void;
  pause(): void;
  next(): void;               // index+1, pauses at end
  prev(): void;
  seek(i: number): void;
  reset(): void;
  continueToBreakpoint(): void; // next step whose line ∈ breakpoints
  toggleBreakpoint(line: number): void;
  setSpeed(s: PlaybackState['speed']): void;
}
```

`usePlaybackTicker` runs a `setTimeout` loop while `playing`: base interval 800 ms ÷ speed. On each tick: if next step's line has a breakpoint → pause; else `next()`.

Derived selectors (`selectors.ts`):
- `currentStep`, `previousStep`
- `changedLocals` — names whose value differs from previous step (drives flash animation)
- `currentStdout` — cumulative stdout at this step (console shows output "as it happens")

### 6.3 Execution flow (client)

```
click Run/Visualize
  └─ useRunCode.mutate({ sourceCode, mode })
       ├─ POST /executions               → { id, accessToken }
       ├─ useQuery(['execution', id], refetchInterval: 500ms until terminal status)
       ├─ on terminal:
       │    ├─ GET /output               → console + compile error markers
       │    └─ if VISUALIZE && success-ish → GET /events → Zod validate → playbackStore.load(trace)
       └─ UI states: idle → submitting → queued → running → done | error
```

Execution `accessToken` (for anonymous users) is kept in the query cache, sent as `X-Execution-Token`.

---

## 7. Trace Types (contract mirror)

`src/lib/trace/types.ts` mirrors backend §6. Keep in sync; `schemaVersion` mismatch → show "please refresh" message.

```ts
export type Value =
  | { kind: 'PRIMITIVE'; type: string; value: number | boolean | string }
  | { kind: 'REF'; ref: string }
  | { kind: 'NULL' };

export type HeapObject =
  | { kind: 'STRING'; type: string; value: string; interned?: boolean }
  | { kind: 'ARRAY'; type: string; elements: Value[]; length?: number }
  | { kind: 'OBJECT'; type: string; fields: Record<string, Value> }
  | { kind: 'LIST'; type: string; elements: Value[] }
  | { kind: 'MAP'; type: string; entries: { key: Value; value: Value }[] };

export interface Frame {
  id: string;
  method: string;
  className: string;
  line: number;
  locals: Record<string, Value>;
}

export type EventType = 'LINE' | 'CALL' | 'RETURN' | 'EXCEPTION' | 'OUTPUT' | 'END';

export interface Highlight { ref: string; index?: number; role: 'compare' | 'swap' | 'active' | 'found' | 'pointer'; label?: string }

export interface TraceStep {
  index: number;
  event: EventType;
  line: number;
  stack: Frame[];                       // top of stack = last
  heap: Record<string, HeapObject>;
  stdout: string;
  returnValue?: Value | null;
  exception?: { type: string; message: string } | null;
  highlights?: Highlight[];
}

export interface Trace {
  schemaVersion: 1;
  source: 'JDI' | 'CONCEPT';
  language: 'java';
  truncated: boolean;
  steps: TraceStep[];
}
```

Shared JSON fixtures in `src/test/fixtures/traces/` are the same files the backend tests use, so both sides test against one contract.

---

## 8. Visualizers

### 8.1 Registry

Every visualizer declares when it applies. `VisualizationPanel` shows tabs only for applicable ones and auto-selects the most relevant.

```ts
interface VisualizerDef {
  id: 'variables' | 'memory' | 'array' | 'string' | 'loop' | 'callstack';
  label: string;
  icon: LucideIcon;
  applies(step: TraceStep, trace: Trace): boolean;
  priority(step: TraceStep): number;     // for auto-select
  component: React.ComponentType<{ step: TraceStep; prev?: TraceStep }>;
}
```

Examples: `array.applies` = any local refs an `ARRAY` heap object; `string.applies` = any `STRING` in locals; `memory` and `variables` always apply.

All visualizers are **pure functions of `(step, prev)`** — no internal timers or fetches. That makes them testable with fixtures and makes prev/next/seek work for free.

### 8.2 Per-visualizer design

| Visualizer | Renders | Animation (Framer Motion) |
|---|---|---|
| VariableInspector | Table of locals for top frame (toggle: all frames): name, type, value | Changed value flashes purple; new variable slides in |
| ArrayVisualizer | Row of indexed boxes; index pointers (`i`, `j`, `low/mid/high`) drawn under cells | `layout` animation on swap; pointer slides between cells; `highlights` color cells (compare = blue, swap = purple, found = green) |
| StringVisualizer | Characters as indexed tiles; substring range bracket; builder result row | Char tiles move on reverse/concat; index pointer slides |
| LoopVisualizer | Loop header (init / condition / update), iteration counter, condition result (true/false badge), iteration history | Counter ticks; condition badge flips green/red |
| MemoryVisualizer | Left: stack frames (top on top). Right: heap objects. SVG arrows from REF slots to heap objects. String Pool region for `interned` strings | Frames push/pop; new heap objects fade in; arrows re-route when refs change |
| CallStack | Compact list of frames with method + line, click to inspect | Push/pop slide |

Loop/condition detection for real JDI traces: derive from line revisits (same line seen again with `i` changed). Concept traces from `/visualizer/loops` carry explicit `highlights` instead.

### 8.3 Arrow rendering (memory view)

- Each value slot and heap object registers its DOM rect via a ref map (`useRegisterAnchor(id)`).
- `ReferenceArrows` draws an absolutely positioned SVG over the panel, recomputing on step change and on `ResizeObserver`.
- Simple cubic Bézier paths; arrow color = accent purple, `null` shown as `∅`.

### 8.4 Performance

- Traces can reach 5,000 steps × snapshots. Keep the trace in the store as-is (no deep cloning); selectors read by index.
- Heap view caps visible objects (backend already limits to 200/step); large arrays show the backend's 100-element preview with "+N more".
- `React.memo` on cells keyed by heap id + value.
- Respect `prefers-reduced-motion`: disable movement, keep color changes.

---

## 9. Editor Integration (Monaco)

| Feature | Implementation |
|---|---|
| Java highlighting | Built-in `java` language |
| Theme | Custom `javmotion-dark` (VS Code Dark-based, background `#0B1120`) and `javmotion-light` via `monaco.editor.defineTheme` |
| Font | JetBrains Mono, ligatures off by default |
| Current line | `deltaDecorations` with whole-line class (blue tint) + gutter arrow; reveal line in center if offscreen |
| Breakpoints | Glyph margin click → `toggleBreakpoint(line)`; red dot decoration |
| Compile errors | `monaco.editor.setModelMarkers` from `compileErrors[]` (line/column/message) |
| Runtime exception | Marker on exception line from trace/stderr |
| Format | Phase 1: Monaco's basic formatter; later: backend formatting endpoint (google-java-format) |
| Read-only while playing | `readOnly: true` during visualize playback; editing invalidates the trace (prompt "re-run?") |
| Shortcuts | `Ctrl+Enter` Run, `Ctrl+Shift+Enter` Visualize, `F10` next step, `Shift+F10` prev, `F5` continue to breakpoint |

---

## 10. API Client & Auth

### 10.1 Client

```ts
// lib/api/client.ts
async function api<T>(path: string, init?: RequestInit & { executionToken?: string }): Promise<T>
```
- Prefixes `import.meta.env.VITE_API_BASE_URL`.
- Adds `Authorization: Bearer <accessToken>` when logged in.
- `credentials: 'include'` only for `/auth/*` (refresh cookie).
- On `401`: call `/auth/refresh` once (single-flight promise shared by concurrent requests), retry original request; on failure → clear auth, redirect to `/login` if route requires auth.
- Parses backend error shape into `ApiError { status, code, detail }`; UI maps known `code`s to friendly messages.

### 10.2 Auth model

- Access token kept **in memory only** (Zustand). Refresh token is an `HttpOnly` cookie managed by the browser — JS never sees it.
- On app load: try silent `/auth/refresh`; success → logged in, failure → guest.
- Guests can use Playground, Concepts, Learn. Saving, history, challenge submissions need login (prompt with dialog, keep unsaved code in `editorStore` + `localStorage` draft).

---

## 11. Theming & Brand

### 11.1 Tokens (CSS variables → Tailwind)

```css
/* styles/globals.css */
:root, :root[data-theme="dark"] {
  --jm-bg:           #0B1120;  /* Deep Background */
  --jm-surface:      #111827;  /* Dark Navy panels */
  --jm-surface-2:    #1A2335;  /* cards, slightly lighter */
  --jm-border:       #273449;
  --jm-text:         #F9FAFB;
  --jm-text-muted:   #9CA3AF;
  --jm-primary:      #2563EB;
  --jm-accent:       #A78BFA;
  --jm-success:      #22C55E;
  --jm-error:        #EF4444;
  --jm-warning:      #F59E0B;
}
:root[data-theme="light"] {
  --jm-bg:           #F8FAFC;
  --jm-surface:      #FFFFFF;
  --jm-surface-2:    #F1F5F9;
  --jm-border:       #E2E8F0;
  --jm-text:         #0F172A;
  --jm-text-muted:   #64748B;
  --jm-primary:      #2563EB;
  --jm-accent:       #7C3AED;  /* darker purple for contrast on light */
  --jm-success:      #16A34A;
  --jm-error:        #DC2626;
  --jm-warning:      #D97706;
}
body { background: var(--jm-bg); color: var(--jm-text); }
```

```ts
// tailwind.config.ts (excerpt)
colors: {
  bg: 'var(--jm-bg)', surface: 'var(--jm-surface)', 'surface-2': 'var(--jm-surface-2)',
  border: 'var(--jm-border)', fg: 'var(--jm-text)', muted: 'var(--jm-text-muted)',
  primary: 'var(--jm-primary)', accent: 'var(--jm-accent)',
  success: 'var(--jm-success)', error: 'var(--jm-error)', warning: 'var(--jm-warning)',
},
fontFamily: {
  brand: ['"Space Grotesk"', 'sans-serif'],
  sans:  ['Inter', 'system-ui', 'sans-serif'],
  mono:  ['"JetBrains Mono"', 'monospace'],
},
```

- Default theme: dark. Toggle stored in `localStorage`; first visit follows `prefers-color-scheme`.
- Theme switch also swaps the Monaco theme.

### 11.2 Semantic color usage

| Meaning | Token |
|---|---|
| Primary buttons (Run, Save) | `primary` |
| Visualize button, highlights, changed values, arrows | `accent` |
| Success status, "found" cells | `success` |
| Compile/runtime errors, breakpoints | `error` |
| Current executing line | `primary` @ 15% opacity |
| Compare cells | `primary` |
| Swap cells | `accent` |

### 11.3 Logo

`components/brand/Logo.tsx`: inline SVG — code brackets `< >` around a play triangle with two trailing motion lines, primary blue; wordmark "JavMotion" in Space Grotesk. Same SVG exported as `public/favicon.svg`.

---

## 12. Error & Empty States

| Situation | UI |
|---|---|
| Compile error | Status bar red "Compilation failed", Monaco markers, console Errors tab auto-opens |
| Runtime exception | Trace plays up to exception step; exception card in visualization panel; stack trace in console |
| Timeout / limit exceeded | Warning banner with limit name; partial trace still playable if `truncated` |
| Network / API down | Toast + retry; Render cold start (~30 s) → "Waking up server…" message after 3 s pending |
| Rate limited (429) | "Too many runs, try again in Ns" |
| No trace yet | Visualization panel empty state: "Click Visualize to see your code in motion" |

---

## 13. Testing

| Level | Tool | Focus |
|---|---|---|
| Unit | Vitest | `playbackStore` (next/prev/breakpoints), `diff.ts`, selectors, registry `applies` |
| Component | React Testing Library | Each visualizer rendered from fixture traces; controls keyboard access |
| Contract | Vitest + Zod | All fixture traces pass `schema.ts` |
| E2E | Playwright | Write code → Visualize → step through → see variable change; login → save program |
| Accessibility | `@axe-core/playwright` | Contrast in both themes, focus order, labelled icon buttons |

---

## 14. Build & Deployment

### 14.1 Environment

| Var | Dev | Prod |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api/v1` | Render API URL |
| `VITE_APP_BASE` | `/` | `/javmotion/` |

`vite.config.ts`: `base: process.env.VITE_APP_BASE ?? '/'`. Dev server on `5173` (backend CORS already allows it).

### 14.2 GitHub Actions (`.github/workflows/frontend-deploy.yml`)

```
on push to main (paths: javmotion-frontend/**)
  ├─ setup-node, npm ci
  ├─ npm run lint && npm run test -- --run
  ├─ npm run build          (env VITE_API_BASE_URL from repo variable)
  ├─ cp dist/index.html dist/404.html
  └─ actions/upload-pages-artifact → actions/deploy-pages
```

PRs run lint + unit tests + build only.

### 14.3 Budgets

- Initial JS (home page) < 200 KB gzip; Monaco + Framer Motion split into playground chunk.
- Lighthouse performance ≥ 90 on home page.

---

## 15. Phase Mapping

| Phase | Frontend deliverables |
|---|---|
| 1 Foundation | Vite + TS + Tailwind + tokens, router, AppLayout, Logo, Playground 3-pane layout, Monaco with theme, API client, health check call |
| 2 Basic visualization | `lib/trace` types + Zod, `playbackStore` + controls, Variable/Array/String/Loop visualizers, Concepts page using `/visualizer/*` |
| 3 Compiler | Run button, `useRunCode` polling, console panel, compile error markers, status bar |
| 4 Debugging | Visualize mode, current-line decoration, breakpoints, call stack, stdout-by-step |
| 5 Advanced viz | Memory visualizer (stack/heap/arrows/String Pool), object/OOP views, recursion frame animation, LIST/MAP rendering |
| 6 Users & learning | Auth pages + store + refresh flow, programs list/save, history, lessons, challenges, progress |
| 7 Advanced | AI explanation side panel per step, DSA visualizers, language selector |
| 8 Production | E2E + a11y suite in CI, performance budgets, error reporting (Sentry), analytics |

Because Phase 2 concept traces and Phase 4 JDI traces share one schema, every visualizer built in Phase 2 is reused unchanged when real code execution arrives.
