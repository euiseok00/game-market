import { expect, test, type Page } from '@playwright/test'

// The summary API is stubbed here so the run does not depend on a Gemini key or free-tier quota;
// the backend's own tests cover the real summary path.
const summary = {
  pros: ['전투 손맛이 좋다', '퍼즐 설계가 영리하다'],
  cons: ['후반부가 짧다'],
  verdict: '할인 때 꼭 해볼 만한 게임이다.',
  createdAt: '2026-11-09T05:00:00Z',
  reviewSource: 'seed',
  reviewCount: 6,
}

/** Fake speechSynthesis with a Korean voice; records what would be spoken in window.__spoken. */
async function installKoreanVoice(page: Page) {
  await page.addInitScript(() => {
    const w = window as unknown as { __spoken: string[]; __cancelled: number }
    w.__spoken = []
    w.__cancelled = 0
    const synth = {
      getVoices: () => [{ lang: 'ko-KR', name: 'Fake Korean', default: true, localService: true, voiceURI: 'fake-ko' }],
      speak: (u: SpeechSynthesisUtterance) => w.__spoken.push(u.text),
      cancel: () => { w.__cancelled++ },
      addEventListener: () => {},
      removeEventListener: () => {},
    }
    Object.defineProperty(window, 'speechSynthesis', { value: synth, configurable: true })
    // Chromium's real utterance rejects a fake voice object, so swap the class too.
    class FakeUtterance {
      text: string
      lang = ''
      voice: unknown = null
      onend: (() => void) | null = null
      onerror: (() => void) | null = null
      constructor(text: string) { this.text = text }
    }
    Object.defineProperty(window, 'SpeechSynthesisUtterance', { value: FakeUtterance, configurable: true })
  })
}

async function signup(page: Page) {
  const username = `e2e${Date.now()}${Math.floor(Math.random() * 1000)}`
  await page.goto('/signup')
  await page.getByLabel('아이디').fill(username)
  await page.getByLabel('비밀번호').fill('secret123')
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible()
  return username
}

test('anyone can read the example reviews on a game page', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('heading', { name: 'Portal 2' }).click()
  await expect(page.getByRole('heading', { name: '사용자 리뷰 8개' })).toBeVisible()
  await expect(page.getByText('포탈 퍼즐 설계가 영리하다. 풀었을 때 무릎을 탁 치게 된다.')).toBeVisible()
  await expect(page.getByText(/예시 리뷰와 사용자가 쓴 리뷰입니다/)).toBeVisible()
})

test('signup, search, cart, mock checkout, library', async ({ page }) => {
  await signup(page)

  await page.getByRole('searchbox').fill('portal')
  await page.getByRole('button', { name: '검색' }).click()
  await expect(page.getByRole('heading', { name: 'Portal 2' })).toBeVisible()
  await expect(page.getByText('1개 게임')).toBeVisible()

  await page.getByRole('heading', { name: 'Portal 2' }).click()
  await page.getByRole('button', { name: '장바구니 담기' }).click()
  await expect(page.getByText('장바구니에 담았습니다.')).toBeVisible()
  await page.getByRole('button', { name: '장바구니 담기' }).click()
  await expect(page.getByText('이미 장바구니에 있거나 보유한 게임입니다.')).toBeVisible()

  await page.getByRole('link', { name: '장바구니' }).click()
  await expect(page.getByText('₩10,500').last()).toBeVisible()
  await page.getByRole('button', { name: '모의 구매하기' }).click()

  await expect(page).toHaveURL(/\/library$/)
  await expect(page.getByText('구매 완료: 1개 게임, ₩10,500')).toBeVisible()
  await expect(page.getByRole('link', { name: 'Portal 2' })).toBeVisible()

  await page.getByRole('link', { name: 'Portal 2' }).click()
  await page.getByRole('button', { name: '장바구니 담기' }).click()
  await expect(page.getByText('이미 장바구니에 있거나 보유한 게임입니다.')).toBeVisible()
})

test('summary panel shows pros, cons, source and reads it aloud', async ({ page }) => {
  await installKoreanVoice(page)
  await page.route('**/api/games/*/summary', (route) => route.fulfill({ json: summary }))
  await signup(page)

  await page.getByRole('heading', { name: 'Portal 2' }).click()
  await page.getByRole('button', { name: 'AI 리뷰 요약 보기' }).click()
  await expect(page.getByText('전투 손맛이 좋다')).toBeVisible()
  await expect(page.getByText('후반부가 짧다')).toBeVisible()
  await expect(page.getByText('할인 때 꼭 해볼 만한 게임이다.')).toBeVisible()
  await expect(page.getByText('예시 리뷰 6개 기반 요약')).toBeVisible()

  await page.getByRole('button', { name: '음성 재생' }).click()
  const spoken = await page.evaluate(() => (window as unknown as { __spoken: string[] }).__spoken)
  expect(spoken).toHaveLength(1)
  expect(spoken[0]).toContain('할인 때 꼭 해볼 만한 게임이다.')

  await page.getByRole('button', { name: '중지' }).click()
  await expect(page.getByRole('button', { name: '음성 재생' })).toBeEnabled()
})

test('summary error from the server is shown with a retry button', async ({ page }) => {
  await page.route('**/api/games/*/summary', (route) =>
    route.fulfill({ status: 503, json: { code: 'SUMMARY_UNAVAILABLE', message: 'AI 요약을 지금 만들 수 없습니다.' } }))
  await signup(page)

  await page.getByRole('heading', { name: 'Portal 2' }).click()
  await page.getByRole('button', { name: 'AI 리뷰 요약 보기' }).click()
  await expect(page.getByRole('alert')).toHaveText('AI 요약을 지금 만들 수 없습니다.')
  await expect(page.getByRole('button', { name: '다시 시도' })).toBeVisible()
})

test('play is disabled with a hint when the browser has no speech support', async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(window, 'speechSynthesis', { value: undefined, configurable: true })
  })
  await page.route('**/api/games/*/summary', (route) => route.fulfill({ json: summary }))
  await signup(page)

  await page.getByRole('heading', { name: 'Portal 2' }).click()
  await page.getByRole('button', { name: 'AI 리뷰 요약 보기' }).click()
  await expect(page.getByRole('button', { name: '음성 재생' })).toBeDisabled()
  await expect(page.getByText('이 브라우저는 음성 읽기를 지원하지 않습니다.')).toBeVisible()
})

test('anonymous user is sent to login from the cart', async ({ page }) => {
  await page.goto('/cart')
  await expect(page).toHaveURL(/\/login\?next=%2Fcart$/)
  await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible()
})
