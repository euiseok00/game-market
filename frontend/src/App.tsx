import { BrowserRouter, Link, NavLink, Route, Routes, useNavigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import Cart from './pages/Cart'
import GameDetail from './pages/GameDetail'
import Home from './pages/Home'
import Library from './pages/Library'
import Login from './pages/Login'
import Signup from './pages/Signup'

function Header() {
  const { me, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <header className="header">
      <Link to="/" className="brand">GAME MARKET</Link>
      <nav>
        <NavLink to="/" end>스토어</NavLink>
        {me && <NavLink to="/cart">장바구니</NavLink>}
        {me && <NavLink to="/library">라이브러리</NavLink>}
      </nav>
      <div className="account">
        {me ? (
          <>
            <span className="muted">{me.username}</span>
            <button className="ghost" onClick={async () => { await logout(); navigate('/') }}>로그아웃</button>
          </>
        ) : (
          <>
            <NavLink to="/login">로그인</NavLink>
            <NavLink to="/signup">회원가입</NavLink>
          </>
        )}
      </div>
    </header>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Header />
        <main className="main">
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/games/:id" element={<GameDetail />} />
            <Route path="/login" element={<Login />} />
            <Route path="/signup" element={<Signup />} />
            <Route path="/cart" element={<Cart />} />
            <Route path="/library" element={<Library />} />
          </Routes>
        </main>
      </AuthProvider>
    </BrowserRouter>
  )
}
