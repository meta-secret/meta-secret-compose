package core

import androidx.compose.ui.graphics.ImageBitmap

/** Blurs a captured presentation backdrop; never captures or stores camera frames. */
interface OrbitBackdropBlur {
    /** Returns the same image dimensions, with [radiusPx] expressed in physical pixels. */
    suspend fun blur(source: ImageBitmap, radiusPx: Float): ImageBitmap
}
