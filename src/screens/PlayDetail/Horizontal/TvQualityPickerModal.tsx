import { memo, useMemo } from 'react'
import {
  Modal,
  StyleSheet,
  Pressable,
  ScrollView,
} from 'react-native'
import Text from '@/components/common/Text'
import TvPressable from '@/components/tv/TvPressable'
import { TV_PANEL_BG } from '@/config/tvStyles'
import { updateSetting } from '@/core/common'
import { setMusicUrl } from '@/core/player/player'
/** 展示顺序：高音质在前 */
const ORDER: LX.Quality[] = ['flac24bit', '192k', 'ape', 'flac', '320k', '128k', 'wav']

function labelFor(q: LX.Quality): string {
  switch (q) {
    case '128k':
      return '标准（128kbps）'
    case '320k':
      return 'HQ 高品质（320kbps）'
    case 'flac':
      return 'SQ 无损（约 48kHz / 16bit）'
    case 'flac24bit':
      return 'Hi-Res（96kHz / 192kHz / 24bit 等，依音源）'
    case '192k':
      return 'Hi-Res（192kbps 档位）'
    case 'ape':
      return '无损 APE'
    case 'wav':
      return 'WAV'
    default:
      return q
  }
}

export default memo(({ visible, musicInfo, currentQuality, onClose }: {
  visible: boolean
  musicInfo: LX.Music.MusicInfo | null
  currentQuality: LX.Quality
  onClose: () => void
}) => {
  const options = useMemo(() => {
    const meta = musicInfo?.meta
    const qm = meta && musicInfo && musicInfo.source !== 'local'
      ? (meta as LX.Music.MusicInfoOnline['meta'])._qualitys
      : null
    if (!qm) return [] as LX.Quality[]
    return ORDER.filter(k => qm[k])
  }, [musicInfo])

  const pick = (q: LX.Quality) => {
    updateSetting({ 'player.playQuality': q })
    if (musicInfo && musicInfo.source !== 'local') {
      setMusicUrl(musicInfo, true)
    }
    onClose()
  }

  return (
    <Modal animationType="fade" transparent visible={visible} onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={() => {
        onClose()
      }}
      >
        <Pressable style={styles.sheet} onPress={(e) => { e.stopPropagation() }}>
          <Text style={styles.title} size={16} color="#fff">音质</Text>
          <Text style={styles.sub} size={12} color="rgba(255,255,255,0.45)">
            仅显示当前歌曲可用的音质档位
          </Text>
          {
            options.length === 0
              ? (
                  <Text style={styles.empty} size={13} color="rgba(255,255,255,0.5)">
                    本地或暂无多档音质信息
                  </Text>
                )
              : (
                  <ScrollView style={styles.scroll} keyboardShouldPersistTaps="handled">
                    {options.map(q => (
                      <TvPressable
                        key={q}
                        tvFocusVariant="mutedFill"
                        style={[styles.row, q === currentQuality ? styles.rowActive : null]}
                        onPress={() => {
                          pick(q)
                        }}
                      >
                        <Text size={14} color="#fff">{labelFor(q)}</Text>
                        {q === currentQuality ? (
                          <Text size={12} color="rgba(57,255,20,0.9)">当前</Text>
                        ) : null}
                      </TvPressable>
                    ))}
                  </ScrollView>
                )
          }
          <TvPressable tvFocusVariant="mutedFill" style={styles.closeBtn} onPress={() => { onClose() }}>
            <Text size={14} color="#fff">关闭</Text>
          </TvPressable>
        </Pressable>
      </Pressable>
    </Modal>
  )
})

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.72)',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 48,
  },
  sheet: {
    width: '100%',
    maxWidth: 560,
    maxHeight: '80%',
    backgroundColor: TV_PANEL_BG,
    borderRadius: 12,
    paddingVertical: 16,
    paddingHorizontal: 14,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.08)',
  },
  title: {
    marginBottom: 4,
    fontWeight: '600',
  },
  sub: {
    marginBottom: 12,
  },
  scroll: {
    maxHeight: 400,
  },
  empty: {
    paddingVertical: 24,
    textAlign: 'center',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 14,
    paddingHorizontal: 12,
    marginVertical: 3,
    borderRadius: 8,
  },
  rowActive: {
    backgroundColor: 'rgba(255,255,255,0.06)',
  },
  closeBtn: {
    marginTop: 12,
    alignSelf: 'center',
    paddingHorizontal: 24,
    paddingVertical: 10,
    borderRadius: 8,
  },
})
