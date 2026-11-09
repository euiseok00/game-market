import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { formatPrice, orderApi, type Game } from '../api'

type Ordered = { orderedTotal: number; orderedCount: number } | null

export default function Library() {
  const ordered = useLocation().state as Ordered
  const [games, setGames] = useState<Game[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    orderApi.library().then(setGames).catch(() => setError('라이브러리를 불러오지 못했습니다.'))
  }, [])

  return (
    <section>
      {ordered && (
        <p className="notice" role="status">
          구매 완료: {ordered.orderedCount}개 게임, {formatPrice(ordered.orderedTotal)}
        </p>
      )}
      <h1>내 라이브러리</h1>
      {error && <p className="error" role="alert">{error}</p>}
      {games && games.length === 0 && <p className="muted">아직 구매한 게임이 없습니다.</p>}
      <ul className="rows">
        {games?.map((g) => (
          <li key={g.id} className="row">
            <img src={g.imageUrl} alt="" />
            <Link to={`/games/${g.id}`}>{g.name}</Link>
            <span className="tag">{g.genre}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
