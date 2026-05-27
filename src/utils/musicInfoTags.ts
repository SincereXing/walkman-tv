import type { BadgeType } from '@/components/common/Badge'

export const getMusicQualityTag = (
  musicInfo: LX.Music.MusicInfo,
  t: (key: 'quality_lossless_24bit' | 'quality_lossless' | 'quality_high_quality') => string,
): { type: BadgeType | null, text: string } => {
  if (musicInfo.source == 'local') return { type: null, text: '' }

  let info: { type: BadgeType | null, text: string } = { type: null, text: '' }
  if (musicInfo.meta._qualitys.flac24bit) {
    info.type = 'secondary'
    info.text = t('quality_lossless_24bit')
  } else if (musicInfo.meta._qualitys.flac ?? musicInfo.meta._qualitys.ape) {
    info.type = 'secondary'
    info.text = t('quality_lossless')
  } else if (musicInfo.meta._qualitys['320k']) {
    info.type = 'tertiary'
    info.text = t('quality_high_quality')
  }

  return info
}

export const hasMusicMv = (musicInfo: LX.Music.MusicInfo): boolean => {
  if (musicInfo.source == 'local') return false
  return Boolean(
    musicInfo.meta.mvId != null ||
    musicInfo.meta.mvVid != null ||
    musicInfo.meta.mvHash != null,
  )
}
