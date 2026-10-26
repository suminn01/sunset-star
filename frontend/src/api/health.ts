import type { HealthResponse } from '../types/health.ts'

// 서버가 켜져 있는지 확인합니다.
export async function fetchHealth(): Promise<HealthResponse> {
  const res = await fetch('/api/health')
  if (!res.ok) throw new Error(`health check failed: ${res.status}`)
  return (await res.json()) as HealthResponse
}
