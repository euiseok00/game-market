import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, authApi } from '../api'
import { useAuth } from '../auth'

export default function Signup() {
  const navigate = useNavigate()
  const { refresh } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    try {
      await authApi.signup(username, password)
      await authApi.login(username, password)
      await refresh()
      navigate('/')
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) setError('이미 사용 중인 아이디입니다.')
      else if (err instanceof ApiError && err.status === 400) setError('아이디와 비밀번호를 입력해 주세요.')
      else setError('가입하지 못했습니다.')
    }
  }

  return (
    <form className="auth-form" onSubmit={onSubmit}>
      <h1>회원가입</h1>
      <label>
        아이디
        <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" maxLength={50} required />
      </label>
      <label>
        비밀번호
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="new-password" maxLength={100} required />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <button type="submit">가입하기</button>
      <p className="muted">
        이미 계정이 있나요? <Link to="/login">로그인</Link>
      </p>
    </form>
  )
}
