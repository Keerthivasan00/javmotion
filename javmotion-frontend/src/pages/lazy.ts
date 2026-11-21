import { lazy } from 'react'

// Code-split pages. Monaco is heavy — keep it out of the initial bundle.
export const PlaygroundPage = lazy(() => import('./PlaygroundPage'))
export const ConceptsPage = lazy(() => import('./ConceptsPage'))
