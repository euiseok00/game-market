import { expect, test, type Locator, type Page } from '@playwright/test'

// Regenerates docs/screenshots against a running stack with a real Gemini key:
//   SCREENSHOTS=1 npx playwright test screenshots
test.skip(!process.env.SCREENSHOTS, 'set SCREENSHOTS=1 to regenerate README screenshots')
test.use({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 2 })

const out = (name: string) => `../docs/screenshots/${name}`

/** Screenshot from the top of the page down to the bottom of `until`, so no empty canvas is kept. */
async function shotUntil(page: Page, until: Locator, name: string, pad = 32) {
  const box = await until.boundingBox()
  if (!box) throw new Error(`no box for ${name}`)
  const width = page.viewportSize()!.width
  await page.screenshot({ path: out(name), fullPage: true, clip: { x: 0, y: 0, width, height: Math.ceil(box.y + box.height + pad) }, type: 'jpeg', quality: 88 })
}

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

  // Store: the first two rows of the catalogue.
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Portal 2' })).toBeVisible()
  await page.waitForLoadState('networkidle')
  await shotUntil(page, page.locator('.card').nth(7), '1-store.jpg', 12)

  await page.goto('/signup')
  await page.getByLabel('아이디').fill(`demo${Date.now() % 100000}`)
  await page.getByLabel('비밀번호').fill('secret123')
  await page.getByRole('button', { name: '가입하기' }).click()
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible()

  // Detail with the AI summary: the project's main feature.
  await page.getByRole('heading', { name: 'Hades' }).click()
  await page.getByRole('button', { name: 'AI 리뷰 요약 보기' }).click()
  await expect(page.getByText('총평')).toBeVisible({ timeout: 60_000 })
  await page.waitForLoadState('networkidle')
  await shotUntil(page, page.locator('.summary'), '2-detail-summary.jpg')

  // The reviews the summary is built from, with one of the user's own so its edit button shows.
  await page.getByRole('textbox', { name: '리뷰 작성' }).fill('빌드를 바꿔 가며 다시 도전하는 재미가 끝이 없다. 죽어도 이야기가 이어져서 덜 억울하다.')
  await page.getByRole('button', { name: '리뷰 등록' }).click()
  await expect(page.getByRole('button', { name: '수정' })).toBeVisible()
  await page.locator('.reviews').screenshot({ path: out('3-reviews.png') })

  // Cart with two games, then the library after the mock purchase.
  await page.getByRole('button', { name: '장바구니 담기' }).click()
  await page.goto('/')
  await page.getByRole('heading', { name: 'Stardew Valley' }).click()
  await page.getByRole('button', { name: '장바구니 담기' }).click()
  await expect(page.getByText('장바구니에 담았습니다.')).toBeVisible()
  await page.getByRole('link', { name: '장바구니' }).click()
  await expect(page.getByRole('button', { name: '모의 구매하기' })).toBeVisible()
  await page.waitForLoadState('networkidle')
  await shotUntil(page, page.getByText('실제 결제는 일어나지 않고'), '4-cart.png')

  await page.getByRole('button', { name: '모의 구매하기' }).click()
  await expect(page.getByText(/구매 완료/)).toBeVisible()
  await page.waitForLoadState('networkidle')
  await shotUntil(page, page.locator('.rows'), '5-library.png')

  // Phone: the same summary on a 390px screen.
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByRole('heading', { name: 'Hades' }).click()
  await page.getByRole('button', { name: 'AI 리뷰 요약 보기' }).click()
  await expect(page.getByText('총평')).toBeVisible({ timeout: 60_000 })
  await page.waitForLoadState('networkidle')
  await shotUntil(page, page.locator('.summary'), '6-mobile-summary.jpg', 16)
})
