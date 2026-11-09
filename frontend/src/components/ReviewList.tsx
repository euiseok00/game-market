import { useEffect, useState } from 'react'
import { gamesApi, type GameReviews } from '../api'

export default function ReviewList({ gameId }: { gameId: number }) {
  const [data, setData] = useState<GameReviews | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    gamesApi.reviews(gameId).then(setData).catch(() => setError('리뷰를 불러오지 못했습니다.'))
  }, [gameId])

  if (error) return <p className="error" role="alert">{error}</p>
  if (!data) return <p className="muted">리뷰 불러오는 중…</p>

  return (
    <section className="reviews" aria-labelledby="reviews-title">
      <h2 id="reviews-title">사용자 리뷰 {data.reviews.length}개</h2>
      <p className="muted small">
        {data.source === 'steam'
          ? 'Steam 사용자 리뷰입니다. AI 요약은 이 리뷰들로 만들어집니다.'
          : '데모용 예시 리뷰입니다(실제 사용자 리뷰 아님). AI 요약은 이 리뷰들로 만들어집니다.'}
      </p>
      <ul>
        {data.reviews.map((r, i) => (
          <li key={i}>{r}</li>
        ))}
      </ul>
    </section>
  )
}
