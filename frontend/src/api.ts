export type Game = {
  id: number
  steamAppId: number
  name: string
  genre: string
  priceKrw: number
  imageUrl: string
  description: string
}

export type Cart = { items: Game[]; totalKrw: number }
export type Order = { id: number; items: Game[]; totalKrw: number; createdAt: string }
export type Me = { id: number; username: string }
export type Summary = {
  pros: string[]
  cons: string[]
  verdict: string
  createdAt: string
  reviewSource: 'steam' | 'seed' | null
  reviewCount: number | null
}

export type GameReviews = { source: 'steam' | 'seed'; reviews: string[] }

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

type Options = RequestInit & { redirectOn401?: boolean }

/** Every backend call goes through here; a 401 sends the user to the login page. */
export async function api<T>(path: string, { redirectOn401 = true, ...init }: Options = {}): Promise<T> {
  const res = await fetch(path, {
    credentials: 'same-origin',
    ...init,
    headers: init.body ? { 'Content-Type': 'application/json', ...init.headers } : init.headers,
  })
  if (res.status === 401 && redirectOn401) {
    const next = window.location.pathname + window.location.search
    window.location.assign(`/login?next=${encodeURIComponent(next)}`)
  }
  if (!res.ok) {
    let message = res.statusText
    try {
      const body = await res.json()
      message = body.message ?? body.detail ?? message
    } catch {
      // non-JSON error body
    }
    throw new ApiError(res.status, message)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export const formatPrice = (krw: number) => (krw === 0 ? '무료' : `₩${krw.toLocaleString('ko-KR')}`)

export const gamesApi = {
  search: (q: string, genre: string) => {
    const params = new URLSearchParams()
    if (q) params.set('q', q)
    if (genre) params.set('genre', genre)
    const qs = params.toString()
    return api<Game[]>(`/api/games${qs ? `?${qs}` : ''}`)
  },
  get: (id: string | number) => api<Game>(`/api/games/${id}`),
  summary: (id: string | number) => api<Summary>(`/api/games/${id}/summary`),
  reviews: (id: string | number) => api<GameReviews>(`/api/games/${id}/reviews`),
}

export const authApi = {
  me: () => api<Me>('/api/auth/me', { redirectOn401: false }),
  login: (username: string, password: string) =>
    api<Me>('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }), redirectOn401: false }),
  signup: (username: string, password: string) =>
    api<Me>('/api/auth/signup', { method: 'POST', body: JSON.stringify({ username, password }) }),
  logout: () => api<void>('/api/auth/logout', { method: 'POST' }),
}

export const cartApi = {
  get: () => api<Cart>('/api/cart'),
  add: (gameId: number) => api<Cart>(`/api/cart/${gameId}`, { method: 'POST' }),
  remove: (gameId: number) => api<Cart>(`/api/cart/${gameId}`, { method: 'DELETE' }),
}

export const orderApi = {
  checkout: () => api<Order>('/api/orders', { method: 'POST' }),
  library: () => api<Game[]>('/api/library'),
}
