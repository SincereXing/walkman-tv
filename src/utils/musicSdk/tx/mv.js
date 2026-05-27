import { httpFetch } from '../../request'
import { getMusicInfoRaw } from './musicInfo'

// #region debug-point B:tx-mv-report
const reportTxMvDebug = (msg, data) => {
  fetch('http://127.0.0.1:7777/event', {
    method: 'POST',
    body: JSON.stringify({
      sessionId: 'mv-fetch-fail',
      runId: 'pre-fix',
      hypothesisId: 'B',
      location: 'src/utils/musicSdk/tx/mv.js',
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

const getMvMeta = (item) => {
  const mv = item?.mv ?? {}
  const mvVid = pickFirstString(mv.vid, mv.mv_vid)
  const mvId = mv.id ?? mv.mv_id ?? mv.vid ?? null
  return {
    mvId: mvId == null || mvId === '' ? undefined : mvId,
    mvVid: mvVid || undefined,
    coverUrl: pickFirstString(mv.picurl, mv.cover, mv.cover_url),
    title: pickFirstString(mv.title, item?.title),
  }
}

const buildMvPageUrl = (mvVid) => `https://y.qq.com/n/ryqq/mv/${mvVid}`

const buildMvPayload = (mvVid) => JSON.stringify({
  comm: {
    ct: 6,
    cv: 0,
    g_tk: 1646675364,
    uin: 0,
    format: 'json',
    platform: 'yqq',
  },
  mvInfo: {
    module: 'music.video.VideoData',
    method: 'get_video_info_batch',
    param: {
      vidlist: [mvVid],
      required: [
        'vid',
        'type',
        'sid',
        'cover_pic',
        'duration',
        'singers',
        'new_switch_str',
        'video_pay',
        'hint',
        'code',
        'msg',
        'name',
        'desc',
        'playcnt',
        'pubdate',
        'isfav',
        'fileid',
        'filesize_v2',
        'switch_pay_type',
        'pay',
        'pay_info',
        'uploader_headurl',
        'uploader_nick',
        'uploader_uin',
        'uploader_encuin',
        'play_forbid_reason',
      ],
    },
  },
  mvUrl: {
    module: 'music.stream.MvUrlProxy',
    method: 'GetMvUrls',
    param: {
      vids: [mvVid],
      request_type: 10003,
      addrtype: 3,
      format: 264,
      maxFiletype: 60,
    },
  },
})

const getVideoData = async(mvVid) => {
  const { body } = await httpFetch('https://u.y.qq.com/cgi-bin/musicu.fcg', {
    method: 'post',
    headers: {
      'User-Agent': 'Mozilla/5.0',
      Referer: 'https://y.qq.com/',
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: buildMvPayload(mvVid),
  }).promise
  // #region debug-point B:tx-video-data
  reportTxMvDebug('tx mv video data response', {
    mvVid,
    bodyKeys: body && typeof body == 'object' ? Object.keys(body).slice(0, 12) : [],
    mvInfoKeys: body?.mvInfo?.data?.[mvVid] && typeof body.mvInfo.data[mvVid] == 'object' ? Object.keys(body.mvInfo.data[mvVid]).slice(0, 12) : [],
    mvUrlKeys: body?.mvUrl?.data?.[mvVid] && typeof body.mvUrl.data[mvVid] == 'object' ? Object.keys(body.mvUrl.data[mvVid]).slice(0, 12) : [],
    hasMp4: Boolean(body?.mvUrl?.data?.[mvVid]?.mp4?.length),
  })
  // #endregion
  return body
}

const normalizeQualityItem = (item) => {
  if (!item || item.code !== 0) return null
  const base = pickFirstString(item.freeflow_url?.[0], `${item.url?.[0] ?? ''}${item.vkey && item.cn ? `${item.vkey}/${item.cn}?fname=${item.cn}` : ''}`)
  if (!base) return null
  return {
    type: String(item.newFileType ?? item.filetype ?? item.format ?? 'default'),
    order: Number(item.newFileType ?? item.filetype ?? item.format ?? 0) || 0,
    url: base,
  }
}

const compareQuality = (a, b) => {
  return b.order - a.order
}

const getQualityList = (urlInfo) => {
  return (urlInfo?.mp4 ?? []).map(normalizeQualityItem).filter(Boolean).sort(compareQuality).map(({ type, url }) => ({ type, url }))
}

const getMvInfo = async(songInfo) => {
  const rawInfo = await getMusicInfoRaw(songInfo.songmid).catch(() => null)
  // #region debug-point B:tx-raw-info
  reportTxMvDebug('tx raw song detail', {
    songmid: songInfo.songmid,
    hasRawInfo: Boolean(rawInfo),
    rawKeys: rawInfo && typeof rawInfo == 'object' ? Object.keys(rawInfo).slice(0, 16) : [],
    rawMv: rawInfo?.mv ?? null,
  })
  // #endregion
  if (!rawInfo) return null

  const { mvId, mvVid, coverUrl, title } = getMvMeta(rawInfo)
  // #region debug-point B:tx-meta
  reportTxMvDebug('tx mv meta extracted', {
    songmid: songInfo.songmid,
    mvId,
    mvVid,
    coverUrl,
    title,
  })
  // #endregion
  if (mvId == null && !mvVid) return null

  return {
    source: 'tx',
    id: String(mvId ?? mvVid),
    title: title || songInfo.name,
    singer: songInfo.singer,
    coverUrl: coverUrl || songInfo.img || null,
    pageUrl: mvVid ? buildMvPageUrl(mvVid) : undefined,
    mvId,
    mvVid,
  }
}

const getMvUrl = async(songInfo) => {
  // #region debug-point B:tx-entry
  reportTxMvDebug('tx getMvUrl entry', {
    id: songInfo.id,
    songmid: songInfo.songmid,
    name: songInfo.name,
    singer: songInfo.singer,
  })
  // #endregion
  const info = await getMvInfo(songInfo)
  if (!info?.mvVid) return info

  const data = await getVideoData(info.mvVid).catch(() => null)
  const urlInfo = data?.mvUrl?.data?.[info.mvVid]
  const videoInfo = data?.mvInfo?.data?.[info.mvVid]
  const qualitys = getQualityList(urlInfo)
  const url = qualitys[0]?.url ?? null
  // #region debug-point B:tx-result
  reportTxMvDebug('tx getMvUrl resolved', {
    mvVid: info.mvVid,
    hasData: Boolean(data),
    urlInfoKeys: urlInfo && typeof urlInfo == 'object' ? Object.keys(urlInfo).slice(0, 12) : [],
    videoInfoKeys: videoInfo && typeof videoInfo == 'object' ? Object.keys(videoInfo).slice(0, 12) : [],
    qualityCount: qualitys.length,
    firstQuality: qualitys[0]?.type ?? '',
    hasUrl: Boolean(url),
  })
  // #endregion

  return {
    ...info,
    title: pickFirstString(videoInfo?.name, info.title),
    coverUrl: pickFirstString(videoInfo?.cover_pic, info.coverUrl),
    pageUrl: info.mvVid ? buildMvPageUrl(info.mvVid) : info.pageUrl,
    url,
    qualitys,
  }
}

export default {
  getMvInfo,
  getMvUrl,
}
