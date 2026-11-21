import { Suspense } from 'react'
import { createBrowserRouter } from 'react-router-dom'
import { AppLayout } from './layouts/AppLayout'
import { PageLoader } from '@/components/ui/PageLoader'
import { HomePage } from '@/pages/HomePage'
import { ConceptsPage, PlaygroundPage } from '@/pages/lazy'
import { NotFoundPage } from '@/pages/NotFoundPage'

export const router = createBrowserRouter(
  [
    {
      element: <AppLayout />,
      children: [
        { path: '/', element: <HomePage /> },
        {
          path: '/playground',
          element: (
            <Suspense fallback={<PageLoader />}>
              <PlaygroundPage />
            </Suspense>
          ),
        },
        {
          path: '/concepts/:kind?',
          element: (
            <Suspense fallback={<PageLoader />}>
              <ConceptsPage />
            </Suspense>
          ),
        },
        { path: '*', element: <NotFoundPage /> },
      ],
    },
  ],
  { basename: import.meta.env.BASE_URL.replace(/\/$/, '') || '/' },
)
