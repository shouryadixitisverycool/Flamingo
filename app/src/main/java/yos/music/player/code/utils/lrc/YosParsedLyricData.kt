package yos.music.player.code.utils.lrc

data class YosParsedLyricData(
    val entries: List<List<Pair<Float, String>>>,
    val endTimes: List<Float>,
    val otherSideForLines: List<Boolean>,
    val transliterations: List<String?>,
    val subtitles: List<String?>,
    val isTtml: Boolean
)
