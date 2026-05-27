import musicSdk from '@/utils/musicSdk'
import { toOldMusicInfo } from '@/utils'

// #region debug-point A:mv-core-report
const reportMvDebug = (hypothesisId: string, msg: string, data?: Record<string, unknown>) => {
  fetch('http://127.0.0.1:7777/event', {
    method: 'POST',
    body: JSON.stringify({
      sessionId: 'mv-fetch-fail',
      runId: 'pre-fix',
      hypothesisId,
      location: 'src/core/music/mv.ts',
      msg: `[DEBUG] ${msg}`,
      data,
      ts: Date.now(),
    }),
  }).catch(() => {})
}
// #endregion

export const getMusicMvInfo = async(musicInfo: LX.Music.MusicInfo): Promise<LX.Music.MusicVideoInfo | null> => {
  if (musicInfo.source == 'local') return null
  const source = musicSdk[musicInfo.source] as any
  if (!source?.getMvInfo) return null
  return source.getMvInfo(toOldMusicInfo(musicInfo))
}

export const getMusicMvUrl = async(musicInfo: LX.Music.MusicInfo): Promise<LX.Music.MusicVideoInfo | null> => {
  // #region debug-point A:mv-core-entry
  reportMvDebug('A', 'enter getMusicMvUrl', {
    id: musicInfo.id,
    source: musicInfo.source,
    name: musicInfo.name,
    singer: musicInfo.singer,
    hasHash: 'hash' in musicInfo ? Boolean((musicInfo as any).hash) : false,
    hasMvHash: 'mvHash' in musicInfo ? Boolean((musicInfo as any).mvHash) : false,
    hasMvId: 'mvId' in musicInfo ? Boolean((musicInfo as any).mvId) : false,
    hasMvVid: 'mvVid' in musicInfo ? Boolean((musicInfo as any).mvVid) : false,
  })
  // #endregion
  if (musicInfo.source == 'local') return null
  const source = musicSdk[musicInfo.source] as any
  if (!source?.getMvUrl) {
    // #region debug-point A:mv-core-no-source
    reportMvDebug('A', 'source has no getMvUrl', {
      source: musicInfo.source,
    })
    // #endregion
    return null
  }
  try {
    const result = await source.getMvUrl(toOldMusicInfo(musicInfo))
    // #region debug-point A:mv-core-result
    reportMvDebug('A', 'getMusicMvUrl resolved', {
      source: musicInfo.source,
      hasResult: Boolean(result),
      hasUrl: Boolean(result?.url),
      hasPageUrl: Boolean(result?.pageUrl),
      qualityCount: result?.qualitys?.length ?? 0,
      mvId: result?.id,
    })
    // #endregion
    return result
  } catch (error: any) {
    // #region debug-point A:mv-core-error
    reportMvDebug('A', 'getMusicMvUrl threw', {
      source: musicInfo.source,
      message: error?.message ?? String(error),
      stack: error?.stack ? String(error.stack).slice(0, 400) : '',
    })
    // #endregion
    throw error
  }
}
