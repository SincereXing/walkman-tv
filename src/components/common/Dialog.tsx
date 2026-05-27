import { useImperativeHandle, forwardRef, useMemo, useRef } from 'react'
import { View } from 'react-native'
import TvPressable from '@/components/tv/TvPressable'
import { IS_TV_APP } from '@/config/tv'
import { TV_ACCENT_GREEN, TV_PANEL_BG } from '@/config/tvStyles'

import Modal, { type ModalType } from './Modal'
import { Icon } from '@/components/common/Icon'
import { useKeyboard } from '@/utils/hooks'
import { createStyle } from '@/utils/tools'
import { useTheme } from '@/store/theme/hook'
import Text from './Text'
import { scaleSizeH } from '@/utils/pixelRatio'

const HEADER_HEIGHT = IS_TV_APP ? 44 : 20
const styles = createStyle({
  centeredView: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: IS_TV_APP ? 36 : 0,
  },
  modalView: {
    maxWidth: '90%',
    minWidth: IS_TV_APP ? '72%' : '60%',
    maxHeight: '78%',
    // backgroundColor: 'white',
    borderRadius: IS_TV_APP ? 16 : 4,
    // shadowColor: '#000',
    // shadowOffset: {
    //   width: 0,
    //   height: 2,
    // },
    // shadowOpacity: 0.25,
    // shadowRadius: 4,
    elevation: 3,
    overflow: 'hidden',
  },
  header: {
    flexGrow: 0,
    flexShrink: 0,
    flexDirection: 'row',
    alignItems: 'center',
    borderTopLeftRadius: IS_TV_APP ? 16 : 4,
    borderTopRightRadius: IS_TV_APP ? 16 : 4,
    height: HEADER_HEIGHT,
  },
  headerAccent: {
    width: IS_TV_APP ? 4 : 0,
    height: IS_TV_APP ? 20 : 0,
    marginLeft: IS_TV_APP ? 16 : 0,
    marginRight: IS_TV_APP ? 10 : 0,
    borderRadius: 999,
    backgroundColor: TV_ACCENT_GREEN,
  },
  title: {
    paddingLeft: IS_TV_APP ? 0 : 5,
    paddingRight: IS_TV_APP ? 54 : 25,
    lineHeight: IS_TV_APP ? undefined : HEADER_HEIGHT,
    fontWeight: IS_TV_APP ? '600' : undefined,
  },
  closeBtn: {
    position: 'absolute',
    right: 0,
    top: 0,
    borderTopRightRadius: IS_TV_APP ? 16 : 4,
    flexGrow: 0,
    flexShrink: 0,
    height: HEADER_HEIGHT,
    justifyContent: 'center',
    alignItems: 'center',
  },
})

export interface DialogProps {
  onHide?: () => void
  keyHide?: boolean
  bgHide?: boolean
  closeBtn?: boolean
  title?: string
  children: React.ReactNode | React.ReactNode[]
  height?: number | `${number}%`
}

export interface DialogType {
  setVisible: (visible: boolean) => void
}

export default forwardRef<DialogType, DialogProps>(({
  onHide,
  keyHide = true,
  bgHide = true,
  closeBtn = true,
  title = '',
  children,
  height,
}: DialogProps, ref) => {
  const theme = useTheme()
  const { keyboardShown, keyboardHeight } = useKeyboard()
  const modalRef = useRef<ModalType>(null)

  useImperativeHandle(ref, () => ({
    setVisible(visible: boolean) {
      modalRef.current?.setVisible(visible)
    },
  }))

  const closeBtnComponent = useMemo(() => {
    return closeBtn
      ? (
          <TvPressable style={{ ...styles.closeBtn, width: scaleSizeH(HEADER_HEIGHT) }} onPress={() => modalRef.current?.setVisible(false)}>
            <Icon name="close" color={theme['c-primary-dark-500-alpha-500']} size={10} />
          </TvPressable>
        )
      : null
  }, [closeBtn, theme])

  return (
    <Modal onHide={onHide} keyHide={keyHide} bgHide={bgHide} bgColor="rgba(50,50,50,.3)" ref={modalRef}>
      <View style={{ ...styles.centeredView, paddingBottom: keyboardShown ? keyboardHeight : 0 }}>
        <View
          style={{
            ...styles.modalView,
            height,
            backgroundColor: IS_TV_APP ? TV_PANEL_BG : theme['c-content-background'],
            borderWidth: IS_TV_APP ? 1 : 0,
            borderColor: IS_TV_APP ? 'rgba(255,255,255,0.08)' : 'transparent',
          }}
          onStartShouldSetResponder={() => true}
        >
          <View
            style={{
              ...styles.header,
              backgroundColor: IS_TV_APP ? 'rgba(255,255,255,0.03)' : theme['c-primary-light-100-alpha-100'],
              borderBottomWidth: IS_TV_APP ? 1 : 0,
              borderBottomColor: IS_TV_APP ? 'rgba(255,255,255,0.06)' : 'transparent',
            }}
          >
            {IS_TV_APP ? <View style={styles.headerAccent} /> : null}
            <Text style={styles.title} size={IS_TV_APP ? 16 : 13} color={IS_TV_APP ? '#fff' : theme['c-primary-light-1000']} numberOfLines={1}>{title}</Text>
            {closeBtnComponent}
          </View>
          {children}
        </View>
      </View>
    </Modal>
  )
})
