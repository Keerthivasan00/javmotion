import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'

export interface PingResponse {
  status: 'UP'
  service: string
  version: string
  time: string
}

export function usePing() {
  return useQuery({
    queryKey: ['system', 'ping'],
    queryFn: () => api<PingResponse>('/system/ping'),
    refetchInterval: 60_000,
    retry: false,
  })
}
