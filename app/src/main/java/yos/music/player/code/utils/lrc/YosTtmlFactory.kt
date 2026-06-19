package yos.music.player.code.utils.lrc

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class YosTtmlFactory {
    fun formatTtmlEntries(ttmlText: String): YosParsedLyricData? {
        if (!isTtmlText(ttmlText)) {
            return null
        }

        return runCatching {
            val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeatureIfSupported("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeatureIfSupported("http://xml.org/sax/features/external-general-entities", false)
                setFeatureIfSupported("http://xml.org/sax/features/external-parameter-entities", false)
            }
            val document = documentBuilderFactory.newDocumentBuilder().parse(
                InputSource(StringReader(ttmlText))
            )
            val rootElement = document.documentElement ?: return null
            if (rootElement.localTagName() != "tt") {
                return null
            }

            val bodyElement = rootElement.descendantsByLocalName("body").firstOrNull() ?: return null
            val subtitleByKey = rootElement.parseMetadataTextByKey("translations")
            val transliterationByKey = rootElement.parseMetadataTextByKey("transliterations")
            val lyricEntries = mutableListOf<List<Pair<Float, String>>>()
            val endTimes = mutableListOf<Float>()
            val otherSideForLines = mutableListOf<Boolean>()
            val transliterations = mutableListOf<String?>()
            val subtitles = mutableListOf<String?>()

            bodyElement.descendantsByLocalName("p").forEach { lineElement ->
                val lineBegin = lineElement.readTimeAttribute("begin") ?: return@forEach
                val wordSegments = lineElement.readWordSegments(lineBegin)
                val lineEnd = lineElement.readTimeAttribute("end")
                    ?: wordSegments.lastOrNull()?.endTime
                    ?: lineBegin
                val linePairs = mutableListOf<Pair<Float, String>>()

                linePairs.add(lineBegin to "")
                if (wordSegments.isNotEmpty()) {
                    wordSegments.forEach { segment ->
                        linePairs.add(segment.endTime to segment.text)
                    }
                } else {
                    val lineText = lineElement.textContent.normalizeMetadataText()
                    if (lineText.isBlank()) {
                        return@forEach
                    }
                    linePairs.add(lineBegin to lineText)
                }
                linePairs.add(lineBegin to "")

                val lyricKey = lineElement.readAttribute("key")
                lyricEntries.add(linePairs)
                endTimes.add(lineEnd.coerceAtLeast(lineBegin))
                otherSideForLines.add(lineElement.readAttribute("agent").equals("v2", ignoreCase = true))
                transliterations.add(lyricKey?.let { transliterationByKey[it] })
                subtitles.add(lyricKey?.let { subtitleByKey[it] })
            }

            if (lyricEntries.isEmpty()) {
                return null
            }

            YosParsedLyricData(
                entries = lyricEntries,
                endTimes = endTimes,
                otherSideForLines = otherSideForLines,
                transliterations = transliterations,
                subtitles = subtitles,
                isTtml = true
            )
        }.getOrNull()
    }

    companion object {
        fun isTtmlText(text: String): Boolean {
            val trimmedText = text.trimStart()
            return trimmedText.startsWith("<") && Regex("<\\s*tt(\\s|>|$)").containsMatchIn(trimmedText)
        }
    }

    private data class TtmlWordSegment(
        val endTime: Float,
        val text: String
    )

    private fun DocumentBuilderFactory.setFeatureIfSupported(feature: String, enabled: Boolean) {
        runCatching {
            setFeature(feature, enabled)
        }
    }

    private fun Element.readWordSegments(lineBegin: Float): List<TtmlWordSegment> {
        val wordSegments = mutableListOf<TtmlWordSegment>()
        val pendingText = StringBuilder()
        val childNodes = childNodes

        for (childIndex in 0 until childNodes.length) {
            val childNode = childNodes.item(childIndex)
            when (childNode.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    pendingText.append(childNode.nodeValue)
                }

                Node.ELEMENT_NODE -> {
                    val childElement = childNode as Element
                    if (childElement.localTagName() == "span") {
                        val segmentEnd = childElement.readTimeAttribute("end")
                            ?: childElement.readTimeAttribute("begin")
                            ?: lineBegin
                        val segmentText = (pendingText.toString() + childElement.textContent)
                            .normalizeSegmentText(wordSegments.isEmpty())
                        pendingText.clear()

                        if (segmentText.isNotEmpty()) {
                            wordSegments.add(TtmlWordSegment(segmentEnd, segmentText))
                        }
                    } else {
                        pendingText.append(childElement.textContent)
                    }
                }
            }
        }

        if (pendingText.isNotBlank() && wordSegments.isNotEmpty()) {
            val lastSegment = wordSegments.removeAt(wordSegments.lastIndex)
            wordSegments.add(
                lastSegment.copy(
                    text = lastSegment.text + pendingText.toString().normalizeSegmentText(false)
                )
            )
        }

        return wordSegments
    }

    private fun Element.parseMetadataTextByKey(containerLocalName: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        descendantsByLocalName(containerLocalName).forEach { containerElement ->
            containerElement.descendantsByLocalName("text").forEach { textElement ->
                val textKey = textElement.readAttribute("for") ?: return@forEach
                val text = textElement.textContent.normalizeMetadataText()
                if (text.isNotBlank() && !result.containsKey(textKey)) {
                    result[textKey] = text
                }
            }
        }

        return result
    }

    private fun Element.descendantsByLocalName(localName: String): List<Element> {
        val result = mutableListOf<Element>()

        fun visit(node: Node) {
            val childNodes = node.childNodes
            for (childIndex in 0 until childNodes.length) {
                val childNode = childNodes.item(childIndex)
                if (childNode.nodeType == Node.ELEMENT_NODE) {
                    val childElement = childNode as Element
                    if (childElement.localTagName() == localName) {
                        result.add(childElement)
                    }
                    visit(childElement)
                }
            }
        }

        visit(this)
        return result
    }

    private fun Element.readTimeAttribute(localName: String): Float? {
        return readAttribute(localName)?.parseTtmlTime()
    }

    private fun Element.readAttribute(localName: String): String? {
        if (hasAttribute(localName)) {
            return getAttribute(localName).takeIf { it.isNotBlank() }
        }

        for (attributeIndex in 0 until attributes.length) {
            val attribute = attributes.item(attributeIndex)
            if (attribute.localName == localName || attribute.nodeName.substringAfter(':') == localName) {
                return attribute.nodeValue.takeIf { it.isNotBlank() }
            }
        }

        return null
    }

    private fun Element.localTagName(): String {
        return localName ?: tagName.substringAfter(':')
    }

    private fun String.parseTtmlTime(): Float? {
        val normalizedTime = trim().removeSuffix("s")
        if (normalizedTime.isBlank()) {
            return null
        }

        val timeParts = normalizedTime.split(':')
        val seconds = when (timeParts.size) {
            1 -> timeParts[0].toFloatOrNull()
            2 -> {
                val minutes = timeParts[0].toFloatOrNull() ?: return null
                val seconds = timeParts[1].toFloatOrNull() ?: return null
                minutes * 60f + seconds
            }
            3 -> {
                val hours = timeParts[0].toFloatOrNull() ?: return null
                val minutes = timeParts[1].toFloatOrNull() ?: return null
                val seconds = timeParts[2].toFloatOrNull() ?: return null
                hours * 3600f + minutes * 60f + seconds
            }
            else -> return null
        } ?: return null

        return seconds * 1000f
    }

    private fun String.normalizeMetadataText(): String {
        return replace(Regex("\\s+"), " ").trim()
    }

    private fun String.normalizeSegmentText(firstSegment: Boolean): String {
        val normalizedText = replace(Regex("\\s+"), " ")
        return if (firstSegment) {
            normalizedText.trimStart()
        } else {
            normalizedText
        }
    }
}
