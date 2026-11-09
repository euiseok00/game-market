import { expect, test } from '@playwright/test'

// Regenerates docs/screenshots against a running stack with a real Gemini key:
//   SCREENSHOTS=1 npx playwright test screenshots
test.skip(!process.env.SCREENSHOTS, 'set SCREENSHOTS=1 to regenerate README screenshots')
test.use({ viewport: { width: 1280, height: 860 } })

const out = (name: string) => `../docs/screenshots/${name}.png`

test('capture README screenshots', async ({ page }) => {
  test.setTimeout(120_000)
  // Headless Chromium has no voices; a fake Korean voice shows the panel as users with one see it.
  await page.addInitScript(() => {
    const synth = {
      getVoices: () => [{ lang: 'ko-KR', name: 'Korean' }],
      speak: () => {},
      cancel: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
    }
    Object.defineProperty(window, 'speechSynthesis', { value: synth, configurable: true })
  })

  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Portal 2' })).toBeVisible()
  await page.waitForLoadState('networkidle')
  await page.screenshot({ path: out('1-store') })

  await page.goto('/signup')
  await page.getByLabel('아이디').fill(`demo${Date.now() % 100000}`)
  await page.getByLabel('비밀번호').fill('secret123')
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible()

  await page.getByRole('heading', { name: 'Hades' }).click()
  await page.getByRole('button', { name: 'AI 리뷰 요약 보기' }).click()
  await expect(page.getByText('총평')).toBeVisible({ timeout: 60_000 })
  await page.getByRole('button', { name: '장바구니 담기' }).click()
  await page.waitForLoadState('networkidle')
  await page.screenshot({ path: out('2-detail-summary'), fullPage: true })

  await page.goto('/')
  await page.getByRole('heading', { name: 'Stardew Valley' }).click()
  await page.getByRole('button', { name: '장바구니 담기' }).click()
  await expect(page.getByText('장바구니에 담았습니다.')).toBeVisible()
  await page.getByRole('link', { name: '장바구니' }).click()
  await expect(page.getByRole('button', { name: '모의 구매하기' })).toBeVisible()
  await page.waitForLoadState('networkidle')
  await page.screenshot({ path: out('3-cart') })

  await page.getByRole('button', { name: '모의 구매하기' }).click()
  await expect(page.getByText(/구매 완료/)).toBeVisible()
  await page.waitForLoadState('networkidle')
  await page.screenshot({ path: out('4-library') })
})
