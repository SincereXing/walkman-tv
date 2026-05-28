package cn.toside.music.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/** Palette ported from the RN TV `config/tvStyles` + iOS `DesignSystem`. Dark, neon-green accent. */
object AppColors {
    val BgDeep = Color(0xFF0A0D14)
    val BgPanel = Color(0xFF14171F)
    val Card = Color(0xFF2A2D38)
    val CardElevated = Color(0xFF333744)

    val AccentGreen = Color(0xFF4ADE80)
    val AccentGreenDim = Color(0x594ADE80)
    val NeonGreen = Color(0xFF39FF14)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA0A0B0)
    val TextMuted = Color(0x6BFFFFFF)

    val NavActiveBg = Color(0x2EFFFFFF)
    val NavInactiveBg = Color(0xFF2A2D38)

    val FocusBorder = AccentGreen
    val FocusFill = Color(0x24FFFFFF)

    val LyricActive = Color(0xFF4ADE80)
    val LyricIdle = Color(0xFF888888)

    // Source tints (酷我/酷狗/QQ/网易/咪咕/本地) — from DesignSystem.swift
    val SourceKw = Color(0xFFF26D4D)
    val SourceKg = Color(0xFF1AB0F0)
    val SourceTx = Color(0xFF33CC8C)
    val SourceWy = Color(0xFFF24552)
    val SourceMg = Color(0xFFF28A1A)
    val SourceLocal = Color(0xFF8E8E93)

    // Quality badge tints
    val QualityHires = Color(0xFFD99E21)
    val QualityLossless = Color(0xFF664DD9)
    val QualityHq = Color(0xFF1A8C6B)
    val QualitySq = Color(0xFF8E8E93)
}
