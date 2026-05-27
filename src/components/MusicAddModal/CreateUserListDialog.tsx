import { useCallback, useEffect, useRef, useState } from 'react'
import { View } from 'react-native'

import Dialog, { type DialogType } from '@/components/common/Dialog'
import Input, { type InputType } from '@/components/common/Input'
import TvPressable from '@/components/tv/TvPressable'
import Text from '@/components/common/Text'
import { createUserList } from '@/core/list'
import { useI18n } from '@/lang'
import { useTheme } from '@/store/theme/hook'
import listState from '@/store/list/state'
import { createStyle, confirmDialog } from '@/utils/tools'
import { IS_TV_APP } from '@/config/tv'
import { TV_ACCENT_GREEN, TV_PANEL_BG } from '@/config/tvStyles'

export default ({
  visible,
  onHide,
}: {
  visible: boolean
  onHide: () => void
}) => {
  const dialogRef = useRef<DialogType>(null)
  const inputRef = useRef<InputType>(null)
  const [text, setText] = useState('')
  const t = useI18n()
  const theme = useTheme()

  useEffect(() => {
    dialogRef.current?.setVisible(visible)
    if (!visible) return
    setText('')
    if (!IS_TV_APP) {
      requestAnimationFrame(() => {
        inputRef.current?.focus()
      })
    }
  }, [visible])

  const handleHide = useCallback(() => {
    setText('')
    onHide()
  }, [onHide])

  const handleSubmit = useCallback(async() => {
    const name = text.trim()
    if (!name.length) return
    if (listState.userList.some(l => l.name == name) && !(await confirmDialog({
      message: global.i18n.t('list_duplicate_tip'),
    }))) return

    handleHide()
    void createUserList(listState.userList.length, [{ id: `userlist_${Date.now()}`, name, locationUpdateTime: null }])
  }, [handleHide, text])

  const handlePressInput = useCallback(() => {
    inputRef.current?.focus()
  }, [])

  return (
    <Dialog
      ref={dialogRef}
      onHide={handleHide}
      title={t('list_create')}
      bgHide={true}
      keyHide={true}
      closeBtn={false}
    >
      <View style={styles.content}>
        <Text size={14} color={theme['c-font-label']} style={styles.label}>
          {t('list_create_input_placeholder')}
        </Text>
        <View style={styles.inputWrap}>
          {IS_TV_APP ? (
            <TvPressable
              tvPreferredFocus
              tvFocusVariant="none"
              style={styles.tvInputBox}
              onPress={handlePressInput}
            >
              <Input
                ref={inputRef}
                placeholder={t('list_create_input_placeholder')}
                value={text}
                onChangeText={setText}
                onSubmitEditing={() => { void handleSubmit() }}
                style={styles.input}
                returnKeyType="done"
              />
            </TvPressable>
          ) : (
            <Input
              ref={inputRef}
              placeholder={t('list_create_input_placeholder')}
              value={text}
              onChangeText={setText}
              onSubmitEditing={() => { void handleSubmit() }}
              style={styles.input}
              returnKeyType="done"
            />
          )}
        </View>
        <View style={styles.btnRow}>
          <TvPressable style={{ ...styles.btn, backgroundColor: IS_TV_APP ? 'rgba(255,255,255,0.08)' : theme['c-button-background'] }} onPress={handleHide}>
            <Text color={theme['c-button-font']}>{t('cancel')}</Text>
          </TvPressable>
          <TvPressable style={{ ...styles.btn, backgroundColor: IS_TV_APP ? TV_ACCENT_GREEN : theme['c-button-background'] }} onPress={() => { void handleSubmit() }}>
            <Text color={IS_TV_APP ? '#0b0d12' : theme['c-button-font']}>{t('confirm')}</Text>
          </TvPressable>
        </View>
      </View>
    </Dialog>
  )
}

const styles = createStyle({
  content: {
    width: IS_TV_APP ? 520 : 360,
    paddingHorizontal: 24,
    paddingTop: 18,
    paddingBottom: 24,
    backgroundColor: IS_TV_APP ? TV_PANEL_BG : undefined,
  },
  label: {
    marginBottom: 12,
  },
  inputWrap: {
    minHeight: IS_TV_APP ? 56 : 44,
    justifyContent: 'center',
  },
  tvInputBox: {
    minHeight: 56,
    borderRadius: 10,
    paddingHorizontal: 10,
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.04)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.12)',
  },
  input: {
    height: IS_TV_APP ? 42 : 36,
    paddingLeft: 12,
    paddingRight: 12,
  },
  btnRow: {
    flexDirection: 'row',
    justifyContent: IS_TV_APP ? 'space-between' : 'flex-end',
    marginTop: 20,
  },
  btn: {
    minWidth: IS_TV_APP ? 160 : 120,
    marginLeft: IS_TV_APP ? 0 : 12,
    paddingVertical: IS_TV_APP ? 12 : 10,
    paddingHorizontal: IS_TV_APP ? 24 : 18,
    borderRadius: IS_TV_APP ? 10 : 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
})
