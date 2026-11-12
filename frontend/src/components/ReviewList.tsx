import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { gamesApi, type GameReviews, type Review } from '../api'
import { useAuth } from '../auth'

const MAX = 2000

/** Textarea + submit, shared by "write a review" and "edit my review". */
function ReviewForm({ initial, label, placeholder, submitLabel, onSubmit, onCancel }: {
  initial: string
  label: string
  placeholder?: string
  submitLabel: string
  onSubmit: (content: string) => Promise<void>
  onCancel?: () => void
}) {
  const [content, setContent] = useState(initial)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await onSubmit(content.trim())
    } catch {
      setError('리뷰를 저장하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="review-form" onSubmit={submit}>
      <textarea aria-label={label} placeholder={placeholder} value={content} maxLength={MAX} onChange={(e) => setContent(e.target.value)} />
      {error && <p className="error" role="alert">{error}</p>}
      <div className="review-actions">
        {onCancel && <button type="button" className="ghost" onClick={onCancel}>취소</button>}
        <button type="submit" disabled={busy || !content.trim()}>{submitLabel}</button>
      </div>
    </form>
  )
}

export default function ReviewList({ gameId }: { gameId: number }) {
  const { me } = useAuth()
  const [data, setData] = useState<GameReviews | null>(null)
  const [error, setError] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [formKey, setFormKey] = useState(0)

  useEffect(() => {
    gamesApi.reviews(gameId).then(setData).catch(() => setError('리뷰를 불러오지 못했습니다.'))
  }, [gameId])

  if (error) return <p className="error" role="alert">{error}</p>
  if (!data) return <p className="muted">리뷰 불러오는 중…</p>

  const add = async (content: string) => {
    const saved = await gamesApi.addReview(gameId, content)
    setData({ ...data, reviews: [...data.reviews, saved] })
    setFormKey((k) => k + 1) // remount to clear the textarea
  }

  const edit = (reviewId: number) => async (content: string) => {
    const saved = await gamesApi.editReview(gameId, reviewId, content)
    setData({ ...data, reviews: data.reviews.map((r) => (r.id === saved.id ? saved : r)) })
    setEditingId(null)
  }

  const mine = (r: Review) => me != null && r.userId === me.id

  return (
    <section className="reviews" aria-labelledby="reviews-title">
      <h2 id="reviews-title">사용자 리뷰 {data.reviews.length}개</h2>
      <p className="muted small">
        예시 리뷰와 사용자가 쓴 리뷰입니다. AI 요약은 이 리뷰들로 만들어지고, 리뷰가 바뀌면 6시간 안에 다시 만들어집니다.
      </p>
      <ul>
        {data.reviews.map((r) => (
          <li key={r.id}>
            {editingId === r.id ? (
              <ReviewForm initial={r.content} label="리뷰 수정" submitLabel="저장" onSubmit={edit(r.id)}
                onCancel={() => setEditingId(null)} />
            ) : (
              <>
                {r.content}
                {mine(r) && (
                  <div className="review-actions">
                    <button type="button" className="ghost" onClick={() => setEditingId(r.id)}>수정</button>
                  </div>
                )}
              </>
            )}
          </li>
        ))}
      </ul>
      {me ? (
        <ReviewForm key={formKey} initial="" label="리뷰 작성"
          placeholder="이 게임 어땠나요? 리뷰는 AI 요약에도 반영됩니다." submitLabel="리뷰 등록" onSubmit={add} />
      ) : (
        <p className="muted">
          <Link to={`/login?next=/games/${gameId}`}>로그인</Link>하면 리뷰를 쓸 수 있습니다.
        </p>
      )}
    </section>
  )
}
