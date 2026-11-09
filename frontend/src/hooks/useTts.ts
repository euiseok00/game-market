import { useCallback, useEffect, useState } from 'react'

type Tts = { supported: boolean; hasKoVoice: boolean; speaking: boolean; play: () => void; stop: () => void }

const synth = (): SpeechSynthesis | undefined =>
  typeof window !== 'undefined' && window.speechSynthesis && typeof SpeechSynthesisUtterance !== 'undefined'
    ? window.speechSynthesis
    : undefined

const koVoice = (voices: SpeechSynthesisVoice[]) =>
  voices.find((v) => v.lang.toLowerCase().replace('_', '-').startsWith('ko'))

/** Browser speechSynthesis wrapper; voices load asynchronously in some browsers (voiceschanged). */
export function useTts(text: string): Tts {
  const supported = synth() !== undefined
  const [voices, setVoices] = useState<SpeechSynthesisVoice[]>(() => synth()?.getVoices() ?? [])
  const [speaking, setSpeaking] = useState(false)

  useEffect(() => {
    const s = synth()
    if (!s) return
    const update = () => setVoices(s.getVoices())
    update()
    s.addEventListener?.('voiceschanged', update)
    return () => {
      s.removeEventListener?.('voiceschanged', update)
      s.cancel()
    }
  }, [])

  const voice = koVoice(voices)

  const play = useCallback(() => {
    const s = synth()
    if (!s || !voice || !text) return
    s.cancel()
    const u = new SpeechSynthesisUtterance(text)
    u.lang = 'ko-KR'
    u.voice = voice
    u.onend = () => setSpeaking(false)
    u.onerror = () => setSpeaking(false)
    s.speak(u)
    setSpeaking(true)
  }, [text, voice])

  const stop = useCallback(() => {
    synth()?.cancel()
    setSpeaking(false)
  }, [])

  return { supported, hasKoVoice: voice !== undefined, speaking, play, stop }
}
