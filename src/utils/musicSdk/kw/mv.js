import { httpFetch } from '../../request'
import musicSearch from './musicSearch'

const normalizeText = (str) => String(str ?? '').replace(/\s+/g, '').toLowerCase()

const getMvIdFromSongInfo = (songInfo) => {
  const mvId = songInfo.mvId
  if (mvId == null || mvId === '' || mvId === 0 || mvId === '0') return ''
  return String(mvId)
}

const buildMvPageUrl = (mvId) => `http://www.kuwo.cn/mvplay/${mvId}`

const isSameSong = (songInfo, item) => {
  const targetName = normalizeText(songInfo.name)
  const targetSinger = normalizeText(songInfo.singer)
  const itemName = normalizeText(item.name)
  const itemSinger = normalizeText(item.singer)
  const sameName = itemName == targetName || itemName.includes(targetName) || targetName.includes(itemName)
  const sameSinger = !targetSinger || itemSinger == targetSinger || itemSinger.includes(targetSinger) || targetSinger.includes(itemSinger)
  return sameName && sameSinger
}

const findMvCandidatesBySearch = async(songInfo) => {
  const keyword = [songInfo.name, songInfo.singer].filter(Boolean).join(' ').trim()
  if (!keyword) return []
  const result = await musicSearch.search(keyword, 1, 10).catch(() => null)
  const list = result?.list ?? []
  return list
    .filter(item => isSameSong(songInfo, item) && item.mvId)
    .sort((a, b) => Number(Boolean(b.mvPlayable)) - Number(Boolean(a.mvPlayable)))
}

const getMvUrlById = async(mvId) => {
  const { body } = await httpFetch(`http://antiserver.kuwo.cn/anti.s?type=convert_url&rid=MV_${encodeURIComponent(mvId)}&format=mp4&response=url`, {
    headers: {
      'User-Agent': 'okhttp/3.10.0',
    },
    format: 'text',
  }).promise

  const url = typeof body == 'string' && /^https?:\/\//.test(body.trim()) ? body.trim() : null
  return url
}

const resolveMvMeta = async(songInfo) => {
  const mvId = getMvIdFromSongInfo(songInfo)
  if (mvId) {
    return {
      mvId,
      coverUrl: songInfo.mvPic ?? songInfo.img ?? null,
      title: songInfo.name,
      singer: songInfo.singer,
    }
  }

  const matched = (await findMvCandidatesBySearch(songInfo))[0]
  if (!matched?.mvId) return null

  return {
    mvId: String(matched.mvId),
    coverUrl: matched.mvPic ?? songInfo.img ?? null,
    title: matched.name ?? songInfo.name,
    singer: matched.singer ?? songInfo.singer,
  }
}

const getMvInfo = async(songInfo) => {
  const meta = await resolveMvMeta(songInfo)
  if (!meta?.mvId) return null

  return {
    source: 'kw',
    id: meta.mvId,
    title: meta.title,
    singer: meta.singer,
    coverUrl: meta.coverUrl,
    pageUrl: buildMvPageUrl(meta.mvId),
    mvId: meta.mvId,
  }
}

const getMvUrl = async(songInfo) => {
  const directMvId = getMvIdFromSongInfo(songInfo)
  const searchCandidates = await findMvCandidatesBySearch(songInfo)
  const candidates = []
  const seen = new Set()

  const pushCandidate = (candidate) => {
    if (!candidate?.mvId) return
    const mvId = String(candidate.mvId)
    if (seen.has(mvId)) return
    seen.add(mvId)
    candidates.push({
      mvId,
      coverUrl: candidate.coverUrl ?? candidate.mvPic ?? songInfo.mvPic ?? songInfo.img ?? null,
      title: candidate.title ?? candidate.name ?? songInfo.name,
      singer: candidate.singer ?? songInfo.singer,
    })
  }

  if (directMvId) {
    pushCandidate({
      mvId: directMvId,
      coverUrl: songInfo.mvPic ?? songInfo.img ?? null,
      title: songInfo.name,
      singer: songInfo.singer,
    })
  }
  searchCandidates.forEach(pushCandidate)

  for (const candidate of candidates) {
    const url = await getMvUrlById(candidate.mvId).catch(() => null)
    if (!url) continue
    return {
      source: 'kw',
      id: candidate.mvId,
      title: candidate.title,
      singer: candidate.singer,
      coverUrl: candidate.coverUrl,
      pageUrl: buildMvPageUrl(candidate.mvId),
      mvId: candidate.mvId,
      url,
      qualitys: [{ type: 'mp4', url }],
    }
  }

  const fallback = candidates[0]
  if (!fallback) return null
  return {
    source: 'kw',
    id: fallback.mvId,
    title: fallback.title,
    singer: fallback.singer,
    coverUrl: fallback.coverUrl,
    pageUrl: buildMvPageUrl(fallback.mvId),
    mvId: fallback.mvId,
    url: null,
    qualitys: [],
  }
}

export default {
  getMvInfo,
  getMvUrl,
}
