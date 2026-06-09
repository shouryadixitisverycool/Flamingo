package yos.music.player.code.utils.others

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import yos.music.player.data.libraries.SettingsLibrary.NowplayingBackgroundEffect

@Stable
object BitmapResolver {
    fun bitmapCompress(bitmap: Bitmap, lowQuality: Boolean = false): Bitmap {
        val px = if (lowQuality) 4 else (if (NowplayingBackgroundEffect) 96 else 32)
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        var compressedBitmap = bitmap

        val size = minOf(originalWidth, originalHeight)
        val xOffset = (originalWidth - size) / 2
        val yOffset = (originalHeight - size) / 2
        val squareBitmap = Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)

        if (size > px) {
            val scaleFactor = size / px
            val scaledSize = size / scaleFactor
            compressedBitmap = Bitmap.createScaledBitmap(squareBitmap, scaledSize, scaledSize, true)
        }

        val config = Bitmap.Config.RGB_565
        return compressedBitmap.copy(config, false)
    }

    fun blurBitmap(bitmap: Bitmap, radius: Int): Bitmap {
        if (radius < 1) {
            return bitmap
        }

        val mutableBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val bitmapWidth = mutableBitmap.width
        val bitmapHeight = mutableBitmap.height
        val bitmapSize = bitmapWidth * bitmapHeight
        val pixelArray = IntArray(bitmapSize)
        val redArray = IntArray(bitmapSize)
        val greenArray = IntArray(bitmapSize)
        val blueArray = IntArray(bitmapSize)
        val minimumCoordinateArray = IntArray(maxOf(bitmapWidth, bitmapHeight))
        val division = radius + radius + 1
        val divisionSum = (division + 1) shr 1
        val divisionLookup = IntArray(256 * divisionSum * divisionSum)
        val stack = Array(division) {
            IntArray(3)
        }

        for (divisionIndex in divisionLookup.indices) {
            divisionLookup[divisionIndex] = divisionIndex / (divisionSum * divisionSum)
        }

        mutableBitmap.getPixels(pixelArray, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)

        var yCoordinate = 0

        while (yCoordinate < bitmapHeight) {
            var incomingRed = 0
            var incomingGreen = 0
            var incomingBlue = 0
            var outgoingRed = 0
            var outgoingGreen = 0
            var outgoingBlue = 0
            var redSum = 0
            var greenSum = 0
            var blueSum = 0
            var stackIndex = 0

            while (stackIndex < division) {
                val xCoordinate = minOf(bitmapWidth - 1, maxOf(stackIndex - radius, 0))
                val pixel = pixelArray[yCoordinate * bitmapWidth + xCoordinate]
                val stackColor = stack[stackIndex]
                stackColor[0] = pixel shr 16 and 0xFF
                stackColor[1] = pixel shr 8 and 0xFF
                stackColor[2] = pixel and 0xFF
                val stackWeight = radius + 1 - kotlin.math.abs(stackIndex - radius)

                redSum += stackColor[0] * stackWeight
                greenSum += stackColor[1] * stackWeight
                blueSum += stackColor[2] * stackWeight

                if (stackIndex <= radius) {
                    outgoingRed += stackColor[0]
                    outgoingGreen += stackColor[1]
                    outgoingBlue += stackColor[2]
                } else {
                    incomingRed += stackColor[0]
                    incomingGreen += stackColor[1]
                    incomingBlue += stackColor[2]
                }
                stackIndex++
            }

            var xCoordinate = 0
            var currentStackIndex = radius

            while (xCoordinate < bitmapWidth) {
                redArray[yCoordinate * bitmapWidth + xCoordinate] = divisionLookup[redSum]
                greenArray[yCoordinate * bitmapWidth + xCoordinate] = divisionLookup[greenSum]
                blueArray[yCoordinate * bitmapWidth + xCoordinate] = divisionLookup[blueSum]

                redSum -= outgoingRed
                greenSum -= outgoingGreen
                blueSum -= outgoingBlue

                var stackStart = currentStackIndex - radius + division
                while (stackStart >= division) {
                    stackStart -= division
                }
                val outgoingStack = stack[stackStart]
                outgoingRed -= outgoingStack[0]
                outgoingGreen -= outgoingStack[1]
                outgoingBlue -= outgoingStack[2]

                if (yCoordinate == 0) {
                    minimumCoordinateArray[xCoordinate] = minOf(xCoordinate + radius + 1, bitmapWidth - 1)
                }

                val nextPixel = pixelArray[yCoordinate * bitmapWidth + minimumCoordinateArray[xCoordinate]]
                outgoingStack[0] = nextPixel shr 16 and 0xFF
                outgoingStack[1] = nextPixel shr 8 and 0xFF
                outgoingStack[2] = nextPixel and 0xFF

                incomingRed += outgoingStack[0]
                incomingGreen += outgoingStack[1]
                incomingBlue += outgoingStack[2]

                redSum += incomingRed
                greenSum += incomingGreen
                blueSum += incomingBlue

                currentStackIndex++
                if (currentStackIndex >= division) {
                    currentStackIndex = 0
                }
                val incomingStack = stack[currentStackIndex]
                outgoingRed += incomingStack[0]
                outgoingGreen += incomingStack[1]
                outgoingBlue += incomingStack[2]
                incomingRed -= incomingStack[0]
                incomingGreen -= incomingStack[1]
                incomingBlue -= incomingStack[2]
                xCoordinate++
            }
            yCoordinate++
        }

        var xCoordinate = 0

        while (xCoordinate < bitmapWidth) {
            var incomingRed = 0
            var incomingGreen = 0
            var incomingBlue = 0
            var outgoingRed = 0
            var outgoingGreen = 0
            var outgoingBlue = 0
            var redSum = 0
            var greenSum = 0
            var blueSum = 0
            var stackIndex = 0

            while (stackIndex < division) {
                val yOffset = minOf(bitmapHeight - 1, maxOf(stackIndex - radius, 0)) * bitmapWidth
                val stackColor = stack[stackIndex]
                stackColor[0] = redArray[yOffset + xCoordinate]
                stackColor[1] = greenArray[yOffset + xCoordinate]
                stackColor[2] = blueArray[yOffset + xCoordinate]
                val stackWeight = radius + 1 - kotlin.math.abs(stackIndex - radius)

                redSum += redArray[yOffset + xCoordinate] * stackWeight
                greenSum += greenArray[yOffset + xCoordinate] * stackWeight
                blueSum += blueArray[yOffset + xCoordinate] * stackWeight

                if (stackIndex <= radius) {
                    outgoingRed += stackColor[0]
                    outgoingGreen += stackColor[1]
                    outgoingBlue += stackColor[2]
                } else {
                    incomingRed += stackColor[0]
                    incomingGreen += stackColor[1]
                    incomingBlue += stackColor[2]
                }
                stackIndex++
            }

            var yCoordinate = 0
            var currentStackIndex = radius

            while (yCoordinate < bitmapHeight) {
                val originalPixel = pixelArray[yCoordinate * bitmapWidth + xCoordinate]
                pixelArray[yCoordinate * bitmapWidth + xCoordinate] = originalPixel and -0x1000000 or
                    (divisionLookup[redSum] shl 16) or
                    (divisionLookup[greenSum] shl 8) or
                    divisionLookup[blueSum]

                redSum -= outgoingRed
                greenSum -= outgoingGreen
                blueSum -= outgoingBlue

                var stackStart = currentStackIndex - radius + division
                while (stackStart >= division) {
                    stackStart -= division
                }
                val outgoingStack = stack[stackStart]
                outgoingRed -= outgoingStack[0]
                outgoingGreen -= outgoingStack[1]
                outgoingBlue -= outgoingStack[2]

                if (xCoordinate == 0) {
                    minimumCoordinateArray[yCoordinate] = minOf(yCoordinate + radius + 1, bitmapHeight - 1) * bitmapWidth
                }

                val nextOffset = minimumCoordinateArray[yCoordinate] + xCoordinate
                outgoingStack[0] = redArray[nextOffset]
                outgoingStack[1] = greenArray[nextOffset]
                outgoingStack[2] = blueArray[nextOffset]

                incomingRed += outgoingStack[0]
                incomingGreen += outgoingStack[1]
                incomingBlue += outgoingStack[2]

                redSum += incomingRed
                greenSum += incomingGreen
                blueSum += incomingBlue

                currentStackIndex++
                if (currentStackIndex >= division) {
                    currentStackIndex = 0
                }
                val incomingStack = stack[currentStackIndex]
                outgoingRed += incomingStack[0]
                outgoingGreen += incomingStack[1]
                outgoingBlue += incomingStack[2]
                incomingRed -= incomingStack[0]
                incomingGreen -= incomingStack[1]
                incomingBlue -= incomingStack[2]
                yCoordinate++
            }
            xCoordinate++
        }

        mutableBitmap.setPixels(pixelArray, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)
        return mutableBitmap
    }
}
