import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Cart from './Cart'

const game = { id: 1, steamAppId: 620, name: 'Portal 2', genre: '액션', priceKrw: 10500, imageUrl: 'p2.jpg', description: '' }

describe('Cart', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('remove failure shows an error and keeps the item', async () => {
    vi.stubGlobal('fetch', vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) =>
      init?.method === 'DELETE'
        ? new Response('{}', { status: 500 })
        : new Response(JSON.stringify({ items: [game], totalKrw: 10500 }), { status: 200 })))

    render(<MemoryRouter><Cart /></MemoryRouter>)
    await userEvent.click(await screen.findByRole('button', { name: 'Portal 2 삭제' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('삭제하지 못했습니다')
    expect(screen.getByText('Portal 2')).toBeInTheDocument()
  })
})
