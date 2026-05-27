import { createHttpFetch } from './utils'
import musicSearch from './musicSearch'

const MG_MV_HOST = 'https://freetyst.nf.migu.cn/public'

const pickFirstString = (...values) => {
  for (const value of values) {
    if (typeof value == 'string' && value.trim()) return value.trim()
  }
  return ''
}

const normalizeText = (str) => String(str ?? '').replace(/\s+/g, '').toLowerCase()

const buildMvUrlByPath = (path) => {
  const value = pickFirstString(path)
  if (!value) return null
  return `${MG_MV_HOST}${value.startsWith('/') ? '' : '/'}${value}`
}

const getMvIdFromSongInfo = (songInfo) => {
  const mvId = songInfo.mvId
  if (mvId == null || mvId === '' || mvId === 0 || mvId === '0') return ''
  return String(mvId)
}

const findMvIdBySearch = async(songInfo) => {
  const keyword = [songInfo.name, songInfo.singer].filter(Boolean).join(' ').trim()
  if (!keyword) return ''
  const result = await musicSearch.search(keyword, 1, 10).catch(() => null)
  const list = result?.list ?? []
  const targetName = normalizeText(songInfo.name)
  const targetSinger = normalizeText(songInfo.singer)
  const matched = list.find(item => {
    const itemName = normalizeText(item.name)
    const itemSinger = normalizeText(item.singer)
    const sameName = itemName == targetName || itemName.includes(targetName) || targetName.includes(itemName)
    const sameSinger = !targetSinger || itemSinger == targetSinger || itemSinger.includes(targetSinger) || targetSinger.includes(itemSinger)
    return sameName && sameSinger && item.mvId
  })
  return matched?.mvId ? String(matched.mvId) : ''
}

const getMvResource = async(mvId) => {
  const data = await createHttpFetch(`https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do?resourceType=D&resourceId=${encodeURIComponent(mvId)}`, {
    headers: {
      'User-Agent': 'Mozilla/5.0',
    },
  })
  return data?.resource?.[0] ?? null
}

const getQualityList = (resource) => {
  const qualitys = []
  const widescreenUrl = buildMvUrlByPath(resource?.widescreenPath)
  const highscreenUrl = buildMvUrlByPath(resource?.highscreenPath)

  if (widescreenUrl) qualitys.push({ type: 'widescreen', url: widescreenUrl })
  if (highscreenUrl && highscreenUrl !== widescreenUrl) qualitys.push({ type: 'highscreen', url: highscreenUrl })

  return qualitys
}

const getMvInfo = async(songInfo) => {
  const mvId = getMvIdFromSongInfo(songInfo) || await findMvIdBySearch(songInfo)
  if (!mvId) return null

  const resource = await getMvResource(mvId).catch(() => null)
  const coverUrl = resource?.imgs?.[0]?.img ?? songInfo.img ?? null

  return {
    source: 'mg',
    id: mvId,
    title: resource?.songName ?? songInfo.name,
    singer: resource?.singer ?? songInfo.singer,
    coverUrl,
    mvId,
  }
}

const getMvUrl = async(songInfo) => {
  const mvId = getMvIdFromSongInfo(songInfo) || await findMvIdBySearch(songInfo)
  if (!mvId) return null

  const resource = await getMvResource(mvId).catch(() => null)
  const info = {
    source: 'mg',
    id: mvId,
    title: resource?.songName ?? songInfo.name,
    singer: resource?.singer ?? songInfo.singer,
    coverUrl: resource?.imgs?.[0]?.img ?? songInfo.img ?? null,
    mvId,
  }
  if (!resource) return info

  const qualitys = getQualityList(resource)
  const url = qualitys[0]?.url ?? null

  return {
    ...info,
    url,
    qualitys,
  }
}

export default {
  getMvInfo,
  getMvUrl,
}
