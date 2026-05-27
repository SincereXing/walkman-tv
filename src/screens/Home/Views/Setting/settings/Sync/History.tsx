import { memo, useRef, useState, useCallback, useImperativeHandle, forwardRef } from 'react'
import { View, ScrollView } from 'react-native'
// import { gzip, ungzip } from 'pako'
import { Icon } from '@/components/common/Icon'
import TvPressable from '@/components/tv/TvPressable'
import { IS_TV_APP } from '@/config/tv'
import { scaleSizeH } from '@/utils/pixelRatio'

import Button from '../../components/Button'
import { getSyncHostHistory, removeSyncHostHistory, setSyncHost } from '@/plugins/sync/data'
import Popup, { type PopupType } from '@/components/common/Popup'
import { BorderWidths } from '@/theme'
import Text from '@/components/common/Text'
import { useTheme } from '@/store/theme/hook'
import { useI18n } from '@/lang'
import { useSettingValue } from '@/store/setting/hook'
import { createStyle } from '@/utils/tools'

type SyncHistoryItem = Awaited<ReturnType<typeof getSyncHostHistory>>[number]

const HistoryListItem = ({ item, index, onRemove, onSelect }: {
  item: SyncHistoryItem
  index: number
  onRemove: (index: number) => void
  onSelect: (index: number) => void
}) => {
  const theme = useTheme()
  const handleSetHost = () => {
    onSelect(index)
    // setHost({
    //   host: item.host,
    //   port: item.port,
    // })
    // setSyncHost({
    //   host: item.host,
    //   port: item.port,
    // })
  }
  const handleRemove = () => {
    onRemove(index)
  }

  return (
    <View style={{ ...styles.listItem, borderBottomColor: theme['c-border-background'], ...(IS_TV_APP ? { minHeight: scaleSizeH(52) } : {}) }}>
      <TvPressable style={IS_TV_APP ? styles.listNameTv : styles.listName} onPress={handleSetHost}>
        <Text numberOfLines={1} size={IS_TV_APP ? 15 : undefined}>{item}</Text>
      </TvPressable>
      <TvPressable onPress={handleRemove} style={IS_TV_APP ? styles.listBtnTv : styles.listBtn}>
        <Icon name="remove" color={theme['c-font-label']} size={IS_TV_APP ? 18 : 12} />
      </TvPressable>
    </View>
  )
}

interface HistoryListProps {
  onSelect: (item: SyncHistoryItem) => void
}
interface HistoryListType {
  show: () => void
}
const HistoryList = forwardRef<HistoryListType, HistoryListProps>(({ onSelect }, ref) => {
  const popupRef = useRef<PopupType>(null)
  const [visible, setVisible] = useState(false)
  const [list, setList] = useState<SyncHistoryItem[]>([])
  // const isUnmountedRef = useRef(true)
  const theme = useTheme()
  const t = useI18n()

  const handleShow = () => {
    popupRef.current?.setVisible(true)
    requestAnimationFrame(() => {
      void getSyncHostHistory().then(historyList => {
        setList([...historyList])
      })
    })
  }
  useImperativeHandle(ref, () => ({
    show() {
      if (visible) handleShow()
      else {
        setVisible(true)
        requestAnimationFrame(() => {
          handleShow()
        })
      }
    },
  }))

  const handleSelect = useCallback((index: number) => {
    popupRef.current?.setVisible(false)
    onSelect(list[index])
  }, [list, onSelect])

  const handleRemove = useCallback((index: number) => {
    void removeSyncHostHistory(index)
    const newList = [...list]
    newList.splice(index, 1)
    setList(newList)
  }, [list])


  return (
    visible
      ? (
          <Popup ref={popupRef} title={t('setting_sync_history_title')}>
            <ScrollView style={styles.list}>
              {
                list.length
                  ? list.map((item, index) => (
                      <HistoryListItem
                        item={item}
                        index={index}
                        onRemove={handleRemove}
                        key={item}
                        onSelect={handleSelect}
                      />
                  ))
                  : <Text style={styles.tipText} color={theme['c-font-label']}>{t('setting_sync_history_empty')}</Text>
              }
            </ScrollView>
          </Popup>
        )
      : null
  )
})

export default memo(({ setHost }: {
  setHost: (host: string) => void
}) => {
  const t = useI18n()
  const isEnableSync = useSettingValue('sync.enable')
  const listRef = useRef<HistoryListType>(null)

  const showPopup = () => {
    listRef.current?.show()
  }

  const handleSelect = (item: SyncHistoryItem) => {
    setHost(item)
    void setSyncHost(item)
  }

  return (
    <>
      <View style={styles.btn}>
        <Button disabled={isEnableSync} onPress={showPopup}>{t('setting_sync_history')}</Button>
      </View>
      <HistoryList ref={listRef} onSelect={handleSelect} />
    </>
  )
})

const styles = createStyle({
  btn: {
    flexDirection: 'row',
    marginLeft: IS_TV_APP ? 12 : 25,
    marginBottom: 15,
  },
  tipText: {
    textAlign: 'center',
    marginTop: 15,
  },
  list: {
    flexShrink: 1,
    flexGrow: 0,
    paddingLeft: 15,
    paddingRight: 15,
  },
  listItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingTop: 8,
    paddingBottom: 8,
    borderBottomWidth: BorderWidths.normal,
  },
  listName: {
    flex: 1,
  },
  listBtn: {
    padding: 5,
  },
  listNameTv: {
    flex: 1,
    justifyContent: 'center',
    paddingVertical: scaleSizeH(10),
    paddingRight: 8,
    minHeight: scaleSizeH(44),
  },
  listBtnTv: {
    paddingHorizontal: 12,
    minWidth: 48,
    minHeight: 48,
    justifyContent: 'center',
    alignItems: 'center',
  },
})
