import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ReviewList from './ReviewList'

function serve(status: number, body: unknown) {
  vi.stubGlobal('fetch', vi.fn(async () =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })))
}

describe('ReviewList', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lists the reviews and labels seed data', async () => {
    serve(200, { source: 'seed', reviews: ['포탈 퍼즐이 영리하다.', '분량이 짧다.'] })
    render(<ReviewList gameId={1} />)
    expect(await screen.findByText('포탈 퍼즐이 영리하다.')).toBeInTheDocument()
    expect(screen.getByText('분량이 짧다.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '사용자 리뷰 2개' })).toBeInTheDocument()
    expect(screen.getByText(/예시 리뷰/)).toBeInTheDocument()
  })

  it('shows a message when reviews cannot be loaded', async () => {
    serve(500, {})
    render(<ReviewList gameId={1} />)
    expect(await screen.findByRole('alert')).toHaveTextContent('리뷰를 불러오지 못했습니다.')
  })
})
