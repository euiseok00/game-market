import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Home from './Home'

const games = [
  { id: 1, steamAppId: 620, name: 'Portal 2', genre: '액션', priceKrw: 10500, imageUrl: 'p2.jpg', description: '' },
  { id: 2, steamAppId: 413150, name: 'Stardew Valley', genre: 'RPG', priceKrw: 16000, imageUrl: 'sv.jpg', description: '' },
]

function mockFetch() {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost')
    const q = (url.searchParams.get('q') ?? '').toLowerCase()
    const body = games.filter((g) => g.name.toLowerCase().includes(q))
    return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('Home', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('Home renders cards from API', async () => {
    mockFetch()
    render(<MemoryRouter><Home /></MemoryRouter>)
    expect(await screen.findByText('Portal 2')).toBeInTheDocument()
    expect(screen.getByText('Stardew Valley')).toBeInTheDocument()
    expect(screen.getByText('₩10,500')).toBeInTheDocument()
  })

  it('Home search filters by name', async () => {
    const fetchMock = mockFetch()
    render(<MemoryRouter><Home /></MemoryRouter>)
    await screen.findByText('Portal 2')

    await userEvent.type(screen.getByRole('searchbox'), 'star')
    await userEvent.click(screen.getByRole('button', { name: '검색' }))

    expect(await screen.findByText('Stardew Valley')).toBeInTheDocument()
    expect(screen.queryByText('Portal 2')).not.toBeInTheDocument()
    expect(String(fetchMock.mock.calls.at(-1)?.[0])).toContain('q=star')
  })
})
