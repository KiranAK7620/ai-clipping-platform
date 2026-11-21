import { useQuery } from '@tanstack/react-query';

type HealthStatus = 'UP' | 'DOWN';
type HealthResponse = { status: HealthStatus; database: HealthStatus };

async function fetchHealth(signal: AbortSignal): Promise<HealthResponse> {
  const response = await fetch('/api/health', { signal, headers: { Accept: 'application/json' } });
  if (response.status !== 200 && response.status !== 503) {
    throw new Error('Service unavailable');
  }

  const data = await response.json();
  const expectedStatus = response.status === 200 ? 'UP' : 'DOWN';
  if (data?.status !== expectedStatus || data?.database !== expectedStatus) {
    throw new Error('Unexpected health response');
  }

  return { status: expectedStatus, database: expectedStatus };
}

export function useHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: ({ signal }) => fetchHealth(signal),
    refetchInterval: 30_000,
    retry: false,
  });
}
