package yos.music.player.code.utils.lrc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YosTtmlFactoryTest {
    @Test
    fun formatTtmlEntries_parsesWordTimedAppleMusicLyrics() {
        val ttml = """
            <?xml version='1.0' encoding='utf-8'?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word" xml:lang="en">
              <head>
                <metadata>
                  <ttm:agent type="person" xml:id="v1"/>
                  <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                    <translations>
                      <translation type="subtitle" xml:lang="en-US">
                        <text for="L1">Hello subtitle</text>
                      </translation>
                    </translations>
                    <transliterations>
                      <transliteration xml:lang="ko-Latn">
                        <text for="L1"><span xmlns="http://www.w3.org/ns/ttml" begin="1.000" end="1.500">hel</span> <span xmlns="http://www.w3.org/ns/ttml" begin="1.500" end="2.000">lo</span></text>
                      </transliteration>
                    </transliterations>
                  </iTunesMetadata>
                </metadata>
              </head>
              <body dur="3:00.000">
                <div begin="1.000" end="2.000">
                  <p begin="1.000" end="2.000" itunes:key="L1" ttm:agent="v1"><span begin="1.000" end="1.500">Hel</span><span begin="1.500" end="2.000">lo</span></p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val result = YosTtmlFactory().formatTtmlEntries(ttml)

        assertNotNull(result)
        result!!
        assertTrue(result.isTtml)
        assertEquals(1, result.entries.size)
        assertEquals(1000f, result.entries[0][0].first, 0.1f)
        assertEquals("Hel", result.entries[0][1].second)
        assertEquals(1500f, result.entries[0][1].first, 0.1f)
        assertEquals("lo", result.entries[0][2].second)
        assertEquals(2000f, result.endTimes[0], 0.1f)
        assertEquals("hel lo", result.transliterations[0])
        assertEquals("Hello subtitle", result.subtitles[0])
        assertFalse(result.otherSideForLines[0])
    }

    @Test
    fun formatTtmlEntries_parsesLineTimedLyricsAndV2Agent() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body dur="00:03:40.750">
                <div begin="00:00:03.730" end="00:03:40.750">
                  <p begin="00:00:03.730" end="00:00:08.110" ttm:agent="v2">Basahin mo na ang lahat</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val result = YosTtmlFactory().formatTtmlEntries(ttml)

        assertNotNull(result)
        result!!
        assertEquals(3730f, result.entries[0][0].first, 0.1f)
        assertEquals("Basahin mo na ang lahat", result.entries[0][1].second)
        assertEquals(8110f, result.endTimes[0], 0.1f)
        assertTrue(result.otherSideForLines[0])
    }

    @Test
    fun formatTtmlEntries_rejectsNonTtmlText() {
        val result = YosTtmlFactory().formatTtmlEntries("[00:01.00]Hello")

        assertNull(result)
    }
}
