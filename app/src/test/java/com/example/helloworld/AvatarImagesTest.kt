package com.example.helloworld

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarImagesTest {
    @Test
    fun thumbnailSamplingKeepsEnoughPixelsForSquareCrop() {
        assertEquals(8, calculateAvatarSampleSize(4032, 3024, 320, squareCrop = true))
        assertEquals(1, calculateAvatarSampleSize(640, 480, 320, squareCrop = true))
    }

    @Test
    fun originalPreviewDoesNotDecodeTheFullCameraBitmap() {
        assertEquals(2, calculateAvatarSampleSize(4032, 3024, 2048, squareCrop = false))
        assertEquals(1, calculateAvatarSampleSize(1280, 720, 2048, squareCrop = false))
    }
}
