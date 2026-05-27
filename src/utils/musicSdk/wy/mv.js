import { httpFetch } from '../../request'
import getMusicInfo from './musicInfo'

const pickFirstString = (...values) => {
  for (const value of values) {
    if (typeof value == 'string' && value.trim()) return value.trim()
  }
  return ''
}

const getMvIdFromSongInfo = (songInfo) => {
  const mvId = songInfo.mvId ?? songInfo.mvid ?? songInfo.mv
  if (mvId == null || mvId === '' || mvId === 0 || mvId === '0') return ''
  return String(mvId)
}

const getMvIdFromRawInfo = (rawInfo) => {
  const mvId = rawInfo?.mv ?? rawInfo?.mvid
  if (mvId == null || mvId === '' || mvId === 0 || mvId === '0') return ''
  return String(mvId)
}

const buildMvPageUrl = (mvId) => `https://music.163.com/#/mv?id=${mvId}`

const normalizeQualityList = (brs) => {
  return Object.entries(brs ?? {})
    .map(([type, url]) => {
      if (typeof url != 'string' || !url) return null
      return {
        type,
        order: parseInt(type) || 0,
        url,
      }
    })
    .filter(Boolean)
    .sort((a, b) => b.order - a.order)
    .map(({ type, url }) => ({ type, url }))
}

const getRawSongInfo = async(songInfo) => {
  return getMusicInfo(songInfo.songmid).promise.catch(() => null)
}

const getMvInfo = async(songInfo) => {
  const existingMvId = getMvIdFromSongInfo(songInfo)
  const rawInfo = existingMvId ? null : await getRawSongInfo(songInfo)
  const mvId = existingMvId || getMvIdFromRawInfo(rawInfo)
  if (!mvId) return null

  return {
    source: 'wy',
    id: mvId,
    title: songInfo.name,
    singer: songInfo.singer,
    coverUrl: songInfo.img ?? rawInfo?.al?.picUrl ?? null,
    pageUrl: buildMvPageUrl(mvId),
    mvId,
  }
}

const getMvUrl = async(songInfo) => {
  const info = await getMvInfo(songInfo)
  if (!info?.mvId) return null

  const { body } = await httpFetch(`https://music.163.com/api/mv/detail?id=${encodeURIComponent(info.mvId)}&type=mp4`, {
    headers: {
      Referer: 'https://music.163.com/',
      origin: 'https://music.163.com',
    },
  }).promise

  if (!body || body.code !== 200 || !body.data) return info

  const qualitys = normalizeQualityList(body.data.brs)
  const url = qualitys[0]?.url ?? null

  return {
    ...info,
    title: pickFirstString(body.data.name, info.title),
    singer: pickFirstString(body.data.artistName, info.singer),
    coverUrl: pickFirstString(body.data.cover, info.coverUrl),
    pageUrl: buildMvPageUrl(info.mvId),
    url,
    qualitys,
  }
}

export default {
  getMvInfo,
  getMvUrl,
}
