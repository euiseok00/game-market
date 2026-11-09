import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { authApi, type Me } from './api'

type AuthState = { me: Me | null; ready: boolean; refresh: () => Promise<void>; logout: () => Promise<void> }

const AuthContext = createContext<AuthState>({
  me: null,
  ready: true,
  refresh: async () => {},
  logout: async () => {},
})

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [ready, setReady] = useState(false)

  const refresh = useCallback(async () => {
    try {
      setMe(await authApi.me())
    } catch {
      setMe(null)
    } finally {
      setReady(true)
    }
  }, [])

  const logout = useCallback(async () => {
    await authApi.logout()
    setMe(null)
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  return <AuthContext.Provider value={{ me, ready, refresh, logout }}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
