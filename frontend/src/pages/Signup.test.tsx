import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Signup from './Signup'

function LoginStub() {
  const state = useLocation().state as { notice?: string } | null
  return <p>login page: {state?.notice}</p>
}

describe('Signup', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('signup ok but auto-login fails sends user to login with a notice', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) =>
      String(input).endsWith('/signup')
        ? new Response(JSON.stringify({ id: 1, username: 'neo' }), { status: 201 })
        : new Response('{}', { status: 500 })))

    render(
      <MemoryRouter initialEntries={['/signup']}>
        <Routes>
          <Route path="/signup" element={<Signup />} />
          <Route path="/login" element={<LoginStub />} />
        </Routes>
      </MemoryRouter>,
    )
    await userEvent.type(screen.getByLabelText('아이디'), 'neo')
    await userEvent.type(screen.getByLabelText('비밀번호'), 'secret123')
    await userEvent.click(screen.getByRole('button', { name: '가입하기' }))

    expect(await screen.findByText(/가입되었습니다/)).toBeInTheDocument()
    expect(screen.queryByText('가입하지 못했습니다.')).not.toBeInTheDocument()
  })
})
