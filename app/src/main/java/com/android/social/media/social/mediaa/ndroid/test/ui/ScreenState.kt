package com.android.social.media.social.mediaa.ndroid.test.ui

import android.graphics.Bitmap

sealed class ScreenState {
    object ImageGrid : ScreenState()
    data class ImageDetail(val index: Int, val bitmap: Bitmap) : ScreenState()
}