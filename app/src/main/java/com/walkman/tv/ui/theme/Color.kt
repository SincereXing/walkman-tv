package com.walkman.tv.ui.theme

import androidx.compose.ui.graphics.Color

/** Palette ported from the RN TV `config/tvStyles` + iOS `DesignSystem`. Dark, neon-green accent.
 *
 *  Token taxonomy:
 *   - Bg* / Card*       → surfaces
 *   - BrandPrimary      → the brand hue itself; do not bind to it directly in UI, use semantics
 *   - Selected / Focus  → interaction states (Focus is a *brighter* sibling of Selected so the
 *                          temporary focus halo reads above the static selected pill)
 *   - Progress / Waveform → playback graphics
 *   - LyricActive       → current line; brighter than Selected so it pops against gray idle lines
 *   - Danger / Warning / Info → functional accents reserved for non-brand semantics
 *   - Text*             → typography ramp
 *   - Source* / Quality* → per-platform identity tints (used in chips + focused-row tint)
 */
object AppColors {
    val BgDeep = Color(0xFF0A0D14)
    val BgPanel = Color(0xFF14171F)
    val Card = Color(0xFF2A2D38)
    val CardElevated = Color(0xFF333744)

    // ── Brand (single hue, three intensities). UI binds to semantic aliases below. ────────
    val BrandPrimary = Color(0xFF4ADE80)          // base — green 500
    val BrandBright  = Color(0xFF7EE7A4)          // lighter — focus halo / lyric active

    // Kept as physical aliases for incremental migration. New code should prefer the
    // semantic tokens below.
    val AccentGreen = BrandPrimary
    val AccentGreenDim = Color(0x594ADE80)
    val NeonGreen = Color(0xFF39FF14)

    // ── Interaction state (semantic) ──────────────────────────────────────────────────────
    val Selected      = BrandPrimary                       // pill 选中 / 当前 tab / now-playing
    val Focus         = BrandBright                        // 焦点环 — 比 Selected 略亮一档
    val Progress      = BrandPrimary.copy(alpha = 0.92f)   // 进度条 / 波形主线
    val WaveformGhost = BrandPrimary.copy(alpha = 0.40f)   // 波形副线

    // ── Functional (non-brand) ───────────────────────────────────────────────────────────
    val Danger  = Color(0xFFEF5A6F)   // 删除 / 退出确定 / 清空 — 红
    val Warning = Color(0xFFE8B341)   // 「已切换音源」「降级到 320k」 — 琥珀
    val Info    = Color(0xFF61AFEF)   // MV badge / 通知 — 蓝

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA0A0B0)
    val TextMuted = Color(0x6BFFFFFF)

    val NavActiveBg = Color(0x2EFFFFFF)
    val NavInactiveBg = Color(0xFF2A2D38)

    val FocusBorder = Focus
    val FocusFill = Color(0x24FFFFFF)

    val LyricActive = BrandBright            // 比 Selected 更亮，区分「当前演唱行」vs「选中」
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
