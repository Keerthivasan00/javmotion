# JavMotion – Frontend

**See Code in Motion.** React playground for writing, running and visualizing Java code.

- Architecture: [docs/architecture/frontend-architecture.md](docs/architecture/frontend-architecture.md)
- Progress: [CHECKLIST.md](CHECKLIST.md)

## Stack

React 19 · Vite 8 · TypeScript (strict) · Tailwind CSS v4 · Monaco Editor · Framer Motion · Zustand · TanStack Query · React Router 7 · Zod · Lucide · Vitest · oxlint · Prettier

## Getting started

```bash
npm install
npm run dev          # http://localhost:5173
```

The header dot shows whether the backend answers `GET /api/v1/system/ping`. Start the backend on port 8080 to turn it green.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Dev server |
| `npm run build` | Type-check + production build to `dist/` |
| `npm run preview` | Serve the production build |
| `npm run lint` | oxlint |
| `npm run format` / `format:check` | Prettier |
| `npm test` / `npm run test:run` | Vitest (watch / once) |

> **Windows note:** open the terminal at `D:\JavaCompailer\...` (capital drive letter). If the path starts with lowercase `d:\`, Vitest loads itself twice and every test fails with "failed to find the current suite".

## Environment

| File | `VITE_API_BASE_URL` | `VITE_APP_BASE` |
|---|---|---|
| `.env.development` | `http://localhost:8080/api/v1` | `/` |
| `.env.production` | Render API URL | `/javmotion/` (GitHub Pages) |

## Folder structure

```
src/
├── app/            App, providers, router, layouts
├── pages/          Route pages (thin — compose features)
├── features/       editor, console, visualizers, system, (playback, execution, auth… later)
├── components/     ui/ primitives, brand/ Logo
├── lib/            api client, trace types, theme, utils
├── styles/         globals.css (Tailwind + brand tokens)
└── test/           setup, fixtures
```

Import alias: `@/` → `src/`. Each feature exposes its public API through `features/<name>/index.ts`.

## Theming

Brand colors live as CSS variables in `src/styles/globals.css` (dark + light) and are exposed as Tailwind utilities: `bg-bg`, `bg-surface`, `bg-surface-2`, `border-border`, `text-fg`, `text-muted`, `bg-primary`, `text-accent`, `text-success`, `text-error`. Theme is set via `data-theme` on `<html>`.
