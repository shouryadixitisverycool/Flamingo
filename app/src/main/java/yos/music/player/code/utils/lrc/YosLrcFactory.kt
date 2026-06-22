package yos.music.player.code.utils.lrc

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastJoinToString
import yos.music.player.data.objects.MediaViewModelObject

/**
 * Lrc 歌词文本处理
 */
class YosLrcFactory(private val formatText: Boolean = true) {
    /**
     * Lrc 歌词文本处理方法
     * @param lrcText Lrc 格式的文本
     */
    /*fun formatLrcEntries(lrcText: String): List<Pair<Float, String>> {
        val lrcLines = lrcText.lines()
        return lrcLines.mapNotNull { line ->
            val timeIndex = line.indexOf("]")
            if (timeIndex == -1) return@mapNotNull null
            val timeText = line.substring(1, timeIndex)
            val timeParts = timeText.split(":")
            if (timeParts.size != 2) return@mapNotNull null
            val minutes = timeParts[0].toIntOrNull() ?: return@mapNotNull null
            val seconds = timeParts[1].toFloatOrNull() ?: return@mapNotNull null
            val time = (minutes * 60 + seconds) * 1000
            val lyric = line.substring(timeIndex + 1)
            if (lyric.isBlank() || lyric.trim() == "//") return@mapNotNull null
            time to if (formatText) lyric.replace(Regex("(?!\\n)\\s+"), " ").trim() else lyric
        }
    }*/
    fun formatLrcEntries(lrcText: String): List<List<Pair<Float, String>>> {
        val lrcLines = lrcText.lines()
        val timeLyricPairs = mutableListOf<MutableList<Pair<Float, String>>>()
        lrcLines.fastForEachIndexed { index, line ->
            //将文本中完全相同而且重复的两个时间轴修改为一个
            //比如[12:34.56][12:34.56]改为[12:34.56]
            var remainingLine =
                line.replace(Regex("([\\[\\]]){2,}"), "$1").replace(Regex("<([^>]+)>"), "[$1]")
                    .replace(Regex("(\\[\\d{2}:\\d{2}\\.\\d{2,3}]){2,}"), "$1")
            val currentLinePairs = mutableListOf<Pair<Float, String>>()
            while (remainingLine.isNotEmpty()) {
                /*val timeIndex = remainingLine.indexOf("]")
                if (timeIndex == -1) break
                val timeText = remainingLine.substring(1, timeIndex)
                val timeParts = timeText.split(":")
                if (timeParts.size != 2) break
                val minutes = timeParts[0].toIntOrNull() ?: break
                val seconds = timeParts[1].toFloatOrNull() ?: break
                val time = (minutes * 60 + seconds) * 1000
                remainingLine = remainingLine.substring(timeIndex + 1)
                val nextTimeIndex = remainingLine.indexOf("[")
                val lyric = if (nextTimeIndex != -1) {
                    remainingLine.substring(0, nextTimeIndex)
                } else {
                    remainingLine
                }*/

                val timeIndex = remainingLine.indexOf("[")
                if (timeIndex == -1) break
                val timeAfter = remainingLine.indexOf("]")
                if (timeAfter == -1) break
                val timeText = remainingLine.substring(timeIndex + 1, timeAfter)
                val timeParts = timeText.split(":")
                if (timeParts.size != 2) break
                val minutes = timeParts[0].toIntOrNull() ?: break
                val seconds = timeParts[1].toFloatOrNull() ?: break
                val time = (minutes * 60 + seconds) * 1000

                if (remainingLine.substring(timeAfter + 1, remainingLine.length)
                        .isBlank() && remainingLine.substring(0, timeIndex).isBlank()
                ) {
                    // 检查下一行的时间差
                    if (index + 1 < lrcLines.size) {
                        val nextLine = lrcLines[index + 1]
                        val nextTimeIndex = nextLine.indexOf("[")
                        val nextTimeAfter = nextLine.indexOf("]")
                        if (nextTimeIndex != -1 && nextTimeAfter != -1) {
                            val nextTimeText = nextLine.substring(nextTimeIndex + 1, nextTimeAfter)
                            val nextTimeParts = nextTimeText.split(":")
                            if (nextTimeParts.size == 2) {
                                val nextMinutes = nextTimeParts[0].toIntOrNull()
                                val nextSeconds = nextTimeParts[1].toFloatOrNull()
                                if (nextMinutes != null && nextSeconds != null) {
                                    val nextTime = (nextMinutes * 60 + nextSeconds) * 1000
                                    if (nextTime - time <= 4200) {
                                        // 忽略当前行的处理，进行下一行的处理
                                        break
                                    }
                                }
                            }
                        }
                    } else {
                        // 这是最后一行，且为空行
                        break
                    }
                }

                val nextTimeIndex = remainingLine.substring(timeAfter + 1).indexOf("[")

                // 逐行起始或逐字末尾
                var lyric = remainingLine.substring(0, timeIndex)

                if (lyric.isEmpty()) {
                    // 句子起始
                    lyric = ""
                    currentLinePairs.add(time to lyric.replace(Regex("(?!\\n)\\s+"), " ").trimStart())
                } else {
                    // 正常句子成分
                    if (/*lyric.isNotBlank() && */lyric.trim() != "//") {
                        currentLinePairs.add(
                            time to lyric.replace(Regex("(?!\\n)\\s+"), " ").trimStart()
                        )
                    }
                }

                remainingLine = remainingLine.substring(timeAfter + 1)
                if (nextTimeIndex == -1) {
                    if (lyric == "") {
                        currentLinePairs.add(
                            time to remainingLine.replace("//", "").replace(
                                Regex("(?!\\n)\\s+"),
                                " "
                            ).trimStart()/*.trim()*/
                        )
                    }
                    remainingLine = ""
                }
            }
            if (currentLinePairs.isNotEmpty()) {
                val existingList =
                    timeLyricPairs.find { it.first().first == currentLinePairs.first().first }
                if (existingList != null) {
                    // existingList.remove(currentLinePairs[0].first to "")
                    existingList.addAll(currentLinePairs)
                } else {
                    currentLinePairs.add(currentLinePairs[0].first to "")
                    timeLyricPairs.add(currentLinePairs)
                }
            }
        }
        val processedEntries = processOtherSide(timeLyricPairs)
        return processedEntries.filter { it.isNotEmpty() }
    }

    private fun processOtherSide(lrcEntries: List<List<Pair<Float, String>>>): List<List<Pair<Float, String>>> {
        // 对唱处理
        val otherSideResult = mutableStateListOf<Boolean>()
        var otherSide = false
        var lastSinger: String? = null
        var otherSideFirstTime = false

        val filteredLrcEntries = lrcEntries.map { lines ->
            val lyric = lines.fastJoinToString(separator = "", transform = {
                it.second
            })

            var deleteType = -1

            if (lyric.endsWith(":") || lyric.endsWith("：")) {
                otherSide = !otherSide
            } else if (lines.size > 1) {
                val currentSinger = lines[1].second
                if (currentSinger.matches(Regex(".+\\s*:\\s*"))) {
                    deleteType = 0
                    val fixedOtherSide = currentSinger.toFixedOtherSide()
                    if (fixedOtherSide != null) {
                        otherSide = fixedOtherSide
                    } else if (lastSinger != null && lastSinger == currentSinger) {
                        // 保持 otherSide 不变
                    } else {
                        if (otherSideFirstTime) {
                            otherSide = !otherSide
                        } else {
                            otherSideFirstTime = true
                        }
                    }
                    lastSinger = currentSinger
                }
            }

            /*if (runCatching { lyric.ifNeedMirror() }.getOrDefault(false)) {
                otherSideResult.add(!otherSide)
            } else {
                otherSideResult.add(otherSide)
            }*/

            otherSideResult.add(otherSide)


            lines.filterIndexed { index, char ->
                !((index == 1 && char.second.matches(Regex(".+\\s*:\\s*"))) && deleteType == 0)
            }
        }

        MediaViewModelObject.otherSideForLines.clear()
        MediaViewModelObject.otherSideForLines.addAll(otherSideResult)
        //println(MediaViewModelObject.otherSideForLines)

        //println(filteredLrcEntries)
        return filteredLrcEntries
    }

    private fun String.toFixedOtherSide(): Boolean? {
        val singerTag = this.substringBefore(":").substringBefore("：").trim().lowercase()
        return when (singerTag) {
            "v1" -> false
            "v2" -> true
            else -> null
        }
    }
}

/*
private fun String.ifNeedMirror(): Boolean {
    val directionality = Character.getDirectionality(this.trim().first())
    return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}*/
