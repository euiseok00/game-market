import { useState } from 'react'
import { ApiError, gamesApi, type Summary } from '../api'
import { useTts } from '../hooks/useTts'

type State = { status: 'idle' } | { status: 'loading' } | { status: 'error'; message: string } | { status: 'done'; summary: Summary }

const speechText = (s: Summary) =>
  ['장점.', ...s.pros, '단점.', ...s.cons, '총평.', s.verdict].join(' ')

export default function SummaryPanel({ gameId }: { gameId: number }) {
  const [state, setState] = useState<State>({ status: 'idle' })
  const tts = useTts(state.status === 'done' ? speechText(state.summary) : '')

  const load = async () => {
    setState({ status: 'loading' })
    try {
      setState({ status: 'done', summary: await gamesApi.summary(gameId) })
    } catch (e) {
      const message =
        e instanceof ApiError && e.status === 503 ? e.message || 'AI 요약을 지금 만들 수 없습니다.' : '요약을 불러오지 못했습니다.'
      setState({ status: 'error', message })
    }
  }

  const ttsHint = !tts.supported
    ? '이 브라우저는 음성 읽기를 지원하지 않습니다.'
    : !tts.hasKoVoice
      ? '이 브라우저/OS에 한국어 음성이 없습니다. 음성 설정에서 한국어 음성을 설치해 주세요.'
      : ''

  return (
    <section className="summary" aria-labelledby="summary-title" aria-busy={state.status === 'loading'}>
      <h2 id="summary-title">AI 리뷰 요약</h2>

      {state.status === 'idle' && <button onClick={load}>AI 리뷰 요약 보기</button>}
      {state.status === 'loading' && <p className="muted" role="status">요약 생성 중…</p>}
      {state.status === 'error' && (
        <>
          <p className="error" role="alert">{state.message}</p>
          <button onClick={load}>다시 시도</button>
        </>
      )}

      {state.status === 'done' && (
        <>
          <div className="summary-cols">
            <div>
              <h3>장점</h3>
              <ul>{state.summary.pros.map((p, i) => <li key={i}>{p}</li>)}</ul>
            </div>
            <div>
              <h3>단점</h3>
              <ul>{state.summary.cons.map((c, i) => <li key={i}>{c}</li>)}</ul>
            </div>
          </div>
          <h3>총평</h3>
          <p>{state.summary.verdict}</p>
          <p className="muted small">생성 시각: {new Date(state.summary.createdAt).toLocaleString('ko-KR')}</p>

          <div className="tts">
            <button onClick={tts.play} disabled={!tts.supported || !tts.hasKoVoice || tts.speaking}>음성 재생</button>
            <button className="ghost" onClick={tts.stop} disabled={!tts.speaking}>중지</button>
          </div>
          {ttsHint && <p className="muted small">{ttsHint}</p>}
        </>
      )}
    </section>
  )
}
