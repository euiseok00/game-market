import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, cartApi, formatPrice, gamesApi, type Game } from '../api'
import { useAuth } from '../auth'
import SummaryPanel from '../components/SummaryPanel'

export default function GameDetail() {
  const { id = '' } = useParams()
  const { me } = useAuth()
  const [game, setGame] = useState<Game | null>(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    gamesApi
      .get(id)
      .then(setGame)
      .catch((e) => setError(e instanceof ApiError && e.status === 404 ? '없는 게임입니다.' : '게임을 불러오지 못했습니다.'))
  }, [id])

  const addToCart = async () => {
    if (!game) return
    try {
      await cartApi.add(game.id)
      setNotice('장바구니에 담았습니다.')
    } catch (e) {
      setNotice(e instanceof ApiError && e.status === 409 ? '이미 장바구니에 있거나 보유한 게임입니다.' : '장바구니에 담지 못했습니다.')
    }
  }

  if (error) return <p className="error" role="alert">{error}</p>
  if (!game) return <p className="muted">불러오는 중…</p>

  return (
    <article className="detail">
      <img className="detail-hero" src={game.imageUrl} alt={`${game.name} 대표 이미지`} />
      <div className="detail-info">
        <h1>{game.name}</h1>
        <p className="detail-meta">
          <span className="tag">{game.genre}</span>
          <span className="price">{formatPrice(game.priceKrw)}</span>
        </p>
        <p>{game.description}</p>
        {me ? (
          <button onClick={addToCart}>장바구니 담기</button>
        ) : (
          <p className="muted">
            <Link to={`/login?next=/games/${game.id}`}>로그인</Link>하면 장바구니에 담고 AI 리뷰 요약을 볼 수 있습니다.
          </p>
        )}
        {notice && <p className="notice" role="status">{notice}</p>}
      </div>
      {me && <SummaryPanel gameId={game.id} />}
    </article>
  )
}
