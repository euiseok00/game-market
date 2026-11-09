import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError, cartApi, formatPrice, orderApi, type Cart as CartData } from '../api'

export default function Cart() {
  const navigate = useNavigate()
  const [cart, setCart] = useState<CartData | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    cartApi.get().then(setCart).catch(() => setError('장바구니를 불러오지 못했습니다.'))
  }, [])

  const remove = async (gameId: number) => setCart(await cartApi.remove(gameId))

  const checkout = async () => {
    setBusy(true)
    try {
      const order = await orderApi.checkout()
      navigate('/library', { state: { orderedTotal: order.totalKrw, orderedCount: order.items.length } })
    } catch (e) {
      setError(e instanceof ApiError && e.status === 409 ? '이미 보유한 게임이 있습니다.' : '구매하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  if (error) return <p className="error" role="alert">{error}</p>
  if (!cart) return <p className="muted">불러오는 중…</p>

  return (
    <section>
      <h1>장바구니</h1>
      {cart.items.length === 0 ? (
        <p className="muted">
          장바구니가 비어 있습니다. <Link to="/">스토어 둘러보기</Link>
        </p>
      ) : (
        <>
          <ul className="rows">
            {cart.items.map((g) => (
              <li key={g.id} className="row">
                <img src={g.imageUrl} alt="" />
                <Link to={`/games/${g.id}`}>{g.name}</Link>
                <span className="price">{formatPrice(g.priceKrw)}</span>
                <button className="ghost" onClick={() => remove(g.id)} aria-label={`${g.name} 삭제`}>
                  삭제
                </button>
              </li>
            ))}
          </ul>
          <div className="total">
            <span>합계</span>
            <strong>{formatPrice(cart.totalKrw)}</strong>
          </div>
          <button onClick={checkout} disabled={busy}>
            모의 구매하기
          </button>
          <p className="muted">실제 결제는 일어나지 않고 주문 내역만 만들어집니다.</p>
        </>
      )}
    </section>
  )
}
