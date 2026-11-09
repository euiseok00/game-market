import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SummaryPanel from './SummaryPanel'

const summary = {
  pros: ['전투가 손맛이 좋다'],
  cons: ['최적화가 아쉽다'],
  verdict: '할인 때 사면 만족스럽다.',
  createdAt: '2026-11-09T04:00:00Z',
}

function respond(status: number, body: unknown) {
  let release!: () => void
  const gate = new Promise<void>((r) => (release = r))
  vi.stubGlobal('fetch', vi.fn(async () => {
    await gate
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
  }))
  return release
}

function installSpeech(voices: { lang: string; name: string }[]) {
  const synth = {
    getVoices: vi.fn(() => voices),
    speak: vi.fn(),
    cancel: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }
  vi.stubGlobal('speechSynthesis', synth)
  vi.stubGlobal('SpeechSynthesisUtterance', class {
    text: string
    lang = ''
    voice: unknown = null
    onend: (() => void) | null = null
    onerror: (() => void) | null = null
    constructor(text: string) { this.text = text }
  })
  return synth
}

describe('SummaryPanel', () => {
  beforeEach(() => installSpeech([{ lang: 'ko-KR', name: 'Korean' }]))
  afterEach(() => vi.unstubAllGlobals())

  it('shows loading then pros/cons/verdict', async () => {
    const release = respond(200, summary)
    render(<SummaryPanel gameId={1} />)
    await userEvent.click(screen.getByRole('button', { name: 'AI 리뷰 요약 보기' }))
    expect(screen.getByText('요약 생성 중…')).toBeInTheDocument()

    release()
    expect(await screen.findByText('전투가 손맛이 좋다')).toBeInTheDocument()
    expect(screen.getByText('최적화가 아쉽다')).toBeInTheDocument()
    expect(screen.getByText('할인 때 사면 만족스럽다.')).toBeInTheDocument()
    expect(screen.getByText(/생성 시각/)).toBeInTheDocument()
  })

  it('shows error message on 503', async () => {
    const release = respond(503, { code: 'SUMMARY_UNAVAILABLE', message: 'AI 요약을 지금 만들 수 없습니다.' })
    render(<SummaryPanel gameId={1} />)
    await userEvent.click(screen.getByRole('button', { name: 'AI 리뷰 요약 보기' }))
    release()
    expect(await screen.findByRole('alert')).toHaveTextContent('AI 요약을 지금 만들 수 없습니다.')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })

  it('play button disabled when speechSynthesis missing', async () => {
    vi.unstubAllGlobals()
    vi.stubGlobal('speechSynthesis', undefined)
    const release = respond(200, summary)
    render(<SummaryPanel gameId={1} />)
    await userEvent.click(screen.getByRole('button', { name: 'AI 리뷰 요약 보기' }))
    release()
    await screen.findByText('전투가 손맛이 좋다')
    expect(screen.getByRole('button', { name: '음성 재생' })).toBeDisabled()
    expect(screen.getByText(/음성 읽기를 지원하지 않습니다/)).toBeInTheDocument()
  })

  it('play button disabled without a Korean voice', async () => {
    installSpeech([{ lang: 'en-US', name: 'English' }])
    const release = respond(200, summary)
    render(<SummaryPanel gameId={1} />)
    await userEvent.click(screen.getByRole('button', { name: 'AI 리뷰 요약 보기' }))
    release()
    await screen.findByText('전투가 손맛이 좋다')
    expect(screen.getByRole('button', { name: '음성 재생' })).toBeDisabled()
    expect(screen.getByText(/한국어 음성이 없습니다/)).toBeInTheDocument()
  })

  it('stop cancels speech', async () => {
    const synth = installSpeech([{ lang: 'ko-KR', name: 'Korean' }])
    const release = respond(200, summary)
    render(<SummaryPanel gameId={1} />)
    await userEvent.click(screen.getByRole('button', { name: 'AI 리뷰 요약 보기' }))
    release()
    await screen.findByText('전투가 손맛이 좋다')

    await userEvent.click(screen.getByRole('button', { name: '음성 재생' }))
    expect(synth.speak).toHaveBeenCalledTimes(1)
    const utterance = synth.speak.mock.calls[0][0] as { text: string; lang: string }
    expect(utterance.lang).toBe('ko-KR')
    expect(utterance.text).toContain('할인 때 사면 만족스럽다.')

    await userEvent.click(screen.getByRole('button', { name: '중지' }))
    expect(synth.cancel).toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '음성 재생' })).toBeEnabled()
  })
})
