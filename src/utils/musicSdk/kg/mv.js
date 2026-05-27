import { httpFetch } from '../../request'
import { getMusicInfoRaw } from './musicInfo'
import musicSearch from './musicSearch'

// #region debug-point C:kg-mv-report
const reportKgMvDebug = (msg, data) => {
  fetch('http://127.0.0.1:7777/event', {
    method: 'POST',
    body: JSON.stringify({
      sessionId: 'mv-fetch-fail',
      runId: 'pre-fix',
      hypothesisId: 'C',
      location: 'src/utils/musicSdk/kg/mv.js',
      msg: `[DEBUG] ${msg}`,
      data,
      ts: Date.now(),
    }),
  }).catch(() => {})
}
// #endregion

const pickFirstString = (...values) => {
  for (const value of values) {
    if (typeof value == 'string' && value.trim()) return value.trim()
  }
  return ''
}

const getMvHashFromSongInfo = (songInfo) => {
  return pickFirstString(songInfo.mvHash)
}

const getMvHashFromRawInfo = (rawInfo) => {
  return pickFirstString(
    rawInfo?.mvhash,
    rawInfo?.MvHash,
    rawInfo?.mv_hash,
    rawInfo?.MVHash,
    rawInfo?.base?.mv_hash,
    rawInfo?.base?.mvhash,
  )
}

const buildKgMvPageUrl = (mvHash) => `https://www.kugou.com/mvweb/html/mv_${mvHash}.html`

const normalizeText = (str) => String(str ?? '').replace(/\s+/g, '').toLowerCase()

const findMvHashBySearch = async(songInfo) => {
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
    return sameName && sameSinger && item.mvHash
  })
  // #region debug-point C:kg-search-match
  reportKgMvDebug('kg search mvHash lookup', {
    keyword,
    resultCount: list.length,
    matchedName: matched?.name ?? '',
    matchedSinger: matched?.singer ?? '',
    matchedMvHash: matched?.mvHash ?? '',
  })
  // #endregion
  return matched?.mvHash ?? ''
}

const normalizeQualityItem = (type, item) => {
  const url = pickFirstString(item?.downurl, item?.url)
  if (!url) return null
  return { type, url }
}

const getQualityList = (data) => {
  const qualitys = [
    normalizeQualityItem('uhd', data?.uhd),
    normalizeQualityItem('rq', data?.rq),
    normalizeQualityItem('sq', data?.sq),
    normalizeQualityItem('hd', data?.hd),
    normalizeQualityItem('sd', data?.sd),
    normalizeQualityItem('lq', data?.lq),
  ].filter(Boolean)

  if (qualitys.length) return qualitys

  const directUrl = pickFirstString(data?.downurl, data?.url, data?.mv_url, data?.playurl)
  return directUrl ? [{ type: 'default', url: directUrl }] : []
}

const getMvDataByHash = async(mvHash) => {
  const { body } = await httpFetch(`https://m.kugou.com/app/i/mv.php?cmd=100&hash=${encodeURIComponent(mvHash)}`).promise
  // #region debug-point C:kg-mv-data
  reportKgMvDebug('kg mv.php response', {
    mvHash,
    bodyKeys: body && typeof body == 'object' ? Object.keys(body).slice(0, 12) : [],
    mvdataKeys: body?.mvdata && typeof body.mvdata == 'object' ? Object.keys(body.mvdata).slice(0, 12) : [],
    hasData: Boolean(body?.data),
  })
  // #endregion
  return body?.mvdata ?? body?.data ?? body
}

const resolveMvHash = async(songInfo) => {
  const existingMvHash = getMvHashFromSongInfo(songInfo)
  if (existingMvHash) return existingMvHash
  if (songInfo.hash) {
    const rawInfo = await getMusicInfoRaw(songInfo.hash).catch(() => null)
    const rawMvHash = getMvHashFromRawInfo(rawInfo)
    // #region debug-point C:kg-raw-info
    reportKgMvDebug('kg raw info mvHash lookup', {
      hash: songInfo.hash,
      hasRawInfo: Boolean(rawInfo),
      rawKeys: rawInfo && typeof rawInfo == 'object' ? Object.keys(rawInfo).slice(0, 12) : [],
      rawMvHash,
    })
    // #endregion
    if (rawMvHash) return rawMvHash
  }
  return findMvHashBySearch(songInfo)
}

const getMvInfo = async(songInfo) => {
  const mvHash = await resolveMvHash(songInfo)
  if (!mvHash) return null

  return {
    source: 'kg',
    id: mvHash,
    title: songInfo.name,
    singer: songInfo.singer,
    coverUrl: songInfo.img ?? null,
    pageUrl: buildKgMvPageUrl(mvHash),
    mvHash,
  }
}

const getMvUrl = async(songInfo) => {
  // #region debug-point C:kg-entry
  reportKgMvDebug('kg getMvUrl entry', {
    id: songInfo.id,
    name: songInfo.name,
    singer: songInfo.singer,
    hash: songInfo.hash,
    mvHash: songInfo.mvHash,
  })
  // #endregion
  const info = await getMvInfo(songInfo)
  if (!info?.mvHash) return null

  const data = await getMvDataByHash(info.mvHash)
  const qualitys = getQualityList(data)
  const url = qualitys[0]?.url ?? null
  // #region debug-point C:kg-result
  reportKgMvDebug('kg getMvUrl resolved', {
    mvHash: info.mvHash,
    dataKeys: data && typeof data == 'object' ? Object.keys(data).slice(0, 12) : [],
    qualityCount: qualitys.length,
    firstQuality: qualitys[0]?.type ?? '',
    hasUrl: Boolean(url),
  })
  // #endregion

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
