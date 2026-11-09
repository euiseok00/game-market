import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { formatPrice, gamesApi, type Game } from '../api'

export default function Home() {
  const [games, setGames] = useState<Game[]>([])
  const [genres, setGenres] = useState<string[]>([])
  const [q, setQ] = useState('')
  const [genre, setGenre] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  const load = (query: string, g: string) => {
    setLoading(true)
    gamesApi
      .search(query, g)
      .then((list) => {
        setGames(list)
        setError('')
      })
      .catch(() => setError('게임 목록을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    gamesApi
      .search('', '')
      .then((list) => {
        setGames(list)
        setGenres([...new Set(list.map((x) => x.genre))].sort())
      })
      .catch(() => setError('게임 목록을 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [])

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    load(q.trim(), genre)
  }

  const count = useMemo(() => games.length, [games])

  return (
    <section>
      <form className="toolbar" onSubmit={onSubmit} role="search">
        <input
          type="search"
          aria-label="게임 이름 검색"
          placeholder="게임 이름 검색"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <select aria-label="장르" value={genre} onChange={(e) => { setGenre(e.target.value); load(q.trim(), e.target.value) }}>
          <option value="">전체 장르</option>
          {genres.map((g) => (
            <option key={g} value={g}>{g}</option>
          ))}
        </select>
        <button type="submit">검색</button>
      </form>

      {error && <p className="error" role="alert">{error}</p>}
      {!loading && !error && <p className="muted">{count}개 게임</p>}

      <ul className="grid">
        {games.map((g) => (
          <li key={g.id} className="card">
            <Link to={`/games/${g.id}`}>
              <img src={g.imageUrl} alt="" loading="lazy" />
              <div className="card-body">
                <h3>{g.name}</h3>
                <div className="card-meta">
                  <span className="tag">{g.genre}</span>
                  <span className="price">{formatPrice(g.priceKrw)}</span>
                </div>
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
