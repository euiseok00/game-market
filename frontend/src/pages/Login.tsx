import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, authApi } from '../api'
import { useAuth } from '../auth'

export default function Login() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { refresh } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const next = params.get('next')
  const safeNext = next && next.startsWith('/') && !next.startsWith('//') ? next : '/'

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    try {
      await authApi.login(username, password)
      await refresh()
      navigate(safeNext)
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? '아이디 또는 비밀번호가 틀렸습니다.' : '로그인하지 못했습니다.')
    }
  }

  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <h1>로그인</h1>
      <label>
        아이디
        <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
      </label>
      <label>
        비밀번호
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <button type="submit">로그인</button>
      <p className="muted">
        계정이 없나요? <Link to="/signup">회원가입</Link>
      </p>
    </form>
  )
}
