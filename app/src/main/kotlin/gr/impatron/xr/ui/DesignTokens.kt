package gr.impatron.xr.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for visual tokens used across pages. Centralising
 * them here keeps the palette consistent and makes future re-skinning trivial
 * (e.g. if the Diocese wants a seasonal liturgical accent we only change the
 * gold tones in one place).
 */
internal object Palette {
    val Bg = Color(0xFF080808)
    val BgElev = Color(0xFF111111)
    val Surface = Color(0xFF161616)
    val SurfaceWarm = Color(0xFF1F1B14)
    val Border = Color(0xFF272421)

    val Gold = Color(0xFFC9A86A)
    val GoldLight = Color(0xFFE0C794)
    val GoldDark = Color(0xFF8E754B)
    val GoldSoft = Color(0x33C9A86A)

    val OnSurface = Color(0xFFF5F1E8)
    val OnSurfaceDim = Color(0xFFA9A59C)
    val OnSurfaceMute = Color(0xFF6B6760)

    val Danger = Color(0xFFD35D4B)
}

/** Reusable gradients tuned for our dark + gold theme. */
internal object Gradients {
    val Card = Brush.verticalGradient(
        listOf(Palette.Surface, Palette.Bg),
    )

    val Hero = Brush.verticalGradient(
        listOf(Palette.SurfaceWarm, Palette.Bg),
    )

    val Gold = Brush.linearGradient(
        listOf(Palette.Gold, Palette.GoldDark),
    )
}
