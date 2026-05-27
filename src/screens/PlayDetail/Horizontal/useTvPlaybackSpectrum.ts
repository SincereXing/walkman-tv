import { useEffect, useRef, useState } from 'react'
import { DeviceEventEmitter, Platform, type EmitterSubscription } from 'react-native'
import { useIsPlay } from '@/store/player/hook'
import { PlaybackSpectrumModule } from '@/native/playbackSpectrum'

const EVENT = 'playbackSpectrum'
const BAR_COUNT = 88

function sampleToBars(bands: number[], n: number): number[] {
  const src: number[] = bands.length > 0 ? bands : Array.from({ length: 16 }, () => 0.1)
  const out: number[] = []
  for (let i = 0; i < n; i++) {
    const t = (i / Math.max(1, n - 1)) * (src.length - 1)
    const j = Math.floor(t)
    const f = t - j
    const a = src[j] ?? 0
    const b = src[j + 1] ?? a
    out.push(a * (1 - f) + b * f)
  }
  return out
}

function fakeBars(t: number, n: number): number[] {
  return Array.from({ length: n }, (_, i) => {
    const phase = t * 0.007 + i * 0.085
    const pulse = 0.18 + 0.12 * Math.sin(t * 0.021)
    const base = 0.12 + pulse * Math.sin(phase) + 0.06 * Math.sin(t * 0.014 + i * 0.17)
    return Math.min(1, Math.max(0.06, base))
  })
}

async function startNative(): Promise<boolean> {
  if (Platform.OS !== 'android' || !PlaybackSpectrumModule?.start) return false
  try {
    await PlaybackSpectrumModule.start()
    return true
  } catch {
    return false
  }
}

async function stopNative(): Promise<void> {
  if (Platform.OS !== 'android' || !PlaybackSpectrumModule?.stop) return
  try {
    await PlaybackSpectrumModule.stop()
  } catch {
    /* ignore */
  }
}

/**
 * Android：Visualizer FFT → 波形条高度；失败或非 Android 时用确定性伪动画兜底。
 */
export function useTvPlaybackSpectrum(enabled: boolean) {
  const isPlaying = useIsPlay()
  const [heights, setHeights] = useState<number[]>(() =>
    Array.from({ length: BAR_COUNT }, () => 0.12),
  )
  const smoothed = useRef<number[]>(Array.from({ length: BAR_COUNT }, () => 0.12))
  /** null = 尚未尝试；true = FFT；false = 走伪动画 */
  const [nativeOk, setNativeOk] = useState<boolean | null>(null)
  const rafRef = useRef<number | null>(null)

  useEffect(() => {
    if (!enabled || !isPlaying || Platform.OS !== 'android') {
      void stopNative()
      setNativeOk(null)
      return
    }

    let sub: EmitterSubscription | undefined
    let cancelled = false
    setNativeOk(null)

    void (async() => {
      const ok = await startNative()
      if (cancelled) return
      setNativeOk(ok)
      if (!ok) return
      sub = DeviceEventEmitter.addListener(EVENT, (raw: unknown) => {
        if (!Array.isArray(raw)) return
        const fromNative: unknown[] = raw
        const nums: number[] = fromNative.map((v) => {
          const n = Number(v)
          return Math.max(0, Math.min(1, Number.isFinite(n) ? n : 0))
        })
        const sampled = sampleToBars(nums, BAR_COUNT)
        const next = smoothed.current.map((prev, i) =>
          prev * 0.28 + (sampled[i] ?? 0) * 0.72,
        )
        smoothed.current = next
        setHeights([...next])
      })
    })()

    return () => {
      cancelled = true
      sub?.remove()
      setNativeOk(null)
      void stopNative()
    }
  }, [enabled, isPlaying])

  useEffect(() => {
    if (!enabled || !isPlaying) {
      if (rafRef.current != null) {
        cancelAnimationFrame(rafRef.current)
        rafRef.current = null
      }
      setHeights(prev => prev.map(h => Math.max(0.06, h * 0.82)))
      return
    }

    const needFake =
      Platform.OS !== 'android'
        ? true
        : nativeOk === false

    if (!needFake) {
      if (rafRef.current != null) {
        cancelAnimationFrame(rafRef.current)
        rafRef.current = null
      }
      return
    }

    let t = 0
    const tick = () => {
      t += 18
      setHeights(fakeBars(t, BAR_COUNT))
      rafRef.current = requestAnimationFrame(tick)
    }
    rafRef.current = requestAnimationFrame(tick)
    return () => {
      if (rafRef.current != null) cancelAnimationFrame(rafRef.current)
    }
  }, [enabled, isPlaying, nativeOk])

  return heights
}
