import { Moon, Sun } from 'lucide-react'
import { NavLink, Outlet } from 'react-router-dom'
import { Logo } from '@/components/brand/Logo'
import { IconButton } from '@/components/ui/IconButton'
import { ServerStatus } from '@/features/system'
import { useThemeStore } from '@/lib/theme/themeStore'
import { cn } from '@/lib/utils/cn'

const NAV = [
  { to: '/playground', label: 'Playground' },
  { to: '/concepts', label: 'Concepts' },
]

export function AppLayout() {
  const { theme, toggle } = useThemeStore()

  return (
    <div className="flex h-full flex-col">
      <header className="flex h-14 shrink-0 items-center gap-6 border-b bg-surface px-4">
        <NavLink to="/" aria-label="JavMotion home">
          <Logo />
        </NavLink>
        <nav className="hidden gap-1 sm:flex">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
                  isActive ? 'bg-surface-2 text-fg' : 'text-muted hover:text-fg',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="ml-auto flex items-center gap-3">
          <ServerStatus />
          <IconButton
            label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
            onClick={toggle}
          >
            {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          </IconButton>
        </div>
      </header>
      <main className="min-h-0 flex-1">
        <Outlet />
      </main>
    </div>
  )
}
