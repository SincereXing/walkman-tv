import { memo } from 'react'

import Button, { type BtnProps } from '@/components/common/Button'
import Text from '@/components/common/Text'
import { useTheme } from '@/store/theme/hook'
import { createStyle } from '@/utils/tools'
import { useTvSettingLayout } from '../tvLayoutContext'
import { scaleSizeH, scaleSizeW } from '@/utils/pixelRatio'

type ButtonProps = BtnProps

export default memo(({ disabled, onPress, children }: ButtonProps) => {
  const theme = useTheme()
  const tv = useTvSettingLayout()

  return (
    <Button
      style={{
        ...styles.button,
        ...(tv ? styles.buttonTv : {}),
        backgroundColor: theme['c-button-background'],
      }}
      onPress={onPress}
      disabled={disabled}
    >
      <Text size={tv ? 15 : 14} color={theme['c-button-font']}>{children}</Text>
    </Button>
  )
})

const styles = createStyle({
  button: {
    paddingLeft: 10,
    paddingRight: 10,
    paddingTop: 5,
    paddingBottom: 5,
    borderRadius: 4,
    marginRight: 10,
  },
  buttonTv: {
    paddingHorizontal: scaleSizeW(16),
    paddingVertical: scaleSizeH(12),
    minHeight: scaleSizeH(44),
    borderRadius: 10,
    marginRight: scaleSizeW(12),
    marginBottom: scaleSizeH(8),
  },
})
