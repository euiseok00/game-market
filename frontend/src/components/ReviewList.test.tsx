import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth'
import ReviewList from './ReviewList'

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const seed = { source: 'seed', reviews: [
  { id: 1, content: '포탈 퍼즐이 영리하다.', userId: null },
  { id: 2, content: '내가 쓴 리뷰', userId: 7 },
] }

/** Logged in as user 7 unless loggedIn is false; POST/PUT echo the content back. */
function serve(reviews: unknown, { loggedIn = true, status = 200 } = {}) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url === '/api/auth/me') return loggedIn ? json({ id: 7, username: 'me' }) : json({}, 401)
    if (init?.method === 'POST') return json({ id: 3, content: JSON.parse(String(init.body)).content, userId: 7 }, 201)
    if (init?.method === 'PUT') return json({ id: 2, content: JSON.parse(String(init.body)).content, userId: 7 })
    return json(reviews, status)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const renderList = () => render(<MemoryRouter><AuthProvider><ReviewList gameId={1} /></AuthProvider></MemoryRouter>)

describe('ReviewList', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lists the reviews and labels seed data', async () => {
    serve(seed, { loggedIn: false })
    renderList()
    expect(await screen.findByText('포탈 퍼즐이 영리하다.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '사용자 리뷰 2개' })).toBeInTheDocument()
    expect(screen.getByText(/예시 리뷰/)).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: '로그인' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '수정' })).not.toBeInTheDocument()
  })

  it('shows a message when reviews cannot be loaded', async () => {
    serve({}, { status: 500 })
    renderList()
    expect(await screen.findByRole('alert')).toHaveTextContent('리뷰를 불러오지 못했습니다.')
  })

  it('adds a review and shows it in the list', async () => {
    const fetchMock = serve(seed)
    renderList()
    await userEvent.type(await screen.findByRole('textbox', { name: '리뷰 작성' }), '새 리뷰')
    await userEvent.click(screen.getByRole('button', { name: '리뷰 등록' }))

    expect(await screen.findByText('새 리뷰')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '사용자 리뷰 3개' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '리뷰 작성' })).toHaveValue('')
    expect(fetchMock).toHaveBeenCalledWith('/api/games/1/reviews', expect.objectContaining({ method: 'POST' }))
  })

  it('offers edit only on my own review and saves the change', async () => {
    const fetchMock = serve(seed)
    renderList()
    await userEvent.click(await screen.findByRole('button', { name: '수정' })) // only one: seed review has none

    const box = screen.getByRole('textbox', { name: '리뷰 수정' })
    await userEvent.clear(box)
    await userEvent.type(box, '고친 리뷰')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('고친 리뷰')).toBeInTheDocument()
    expect(screen.queryByText('내가 쓴 리뷰')).not.toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith('/api/games/1/reviews/2', expect.objectContaining({ method: 'PUT' }))
  })
})
