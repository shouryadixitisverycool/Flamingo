# Lyric Formats Supported by Flamingo

Last revised: June 19, 2026


## LRC

> LRC (short for LyRiCs) is a computer file format that synchronizes with audio files (such as MP3, Vorbis, or MIDI). The LRC format is a text format similar to subtitle files for TV and movies.

> Its file extension is .lrc.

Over time, LRC has lacked standardized specifications from any organization, leading to various variants. Flamingo only supports the common types listed below.

Flamingo loads LRC lyrics from embedded audio metadata or from a sidecar `.lrc` file with the same base name as the audio file. Embedded LRC lyrics must still use an LRC-compatible text format.


### Parsing Support

#### Line-by-Line

```  
[Time]Lyrics  
[Time]Translation  
[Time]Another translation  
```  

Supports marking translations via the same timeline. If multiple repeated lines exist, the last repeated line is used as the displayed translation.

```  
[Time]Lyric 1  
[Time]  
[Time]Lyric 2  
```  

Supports marking countdown to singing start via empty time tags.

```  
[Time]v1: Lyrics  
```  

Supports marking singers via prefixes like `v1: `.

```  
[Time]Singer:  
[Time]Lyrics  
```  

Supports marking singers with either an ASCII or full-width colon.


#### Word-by-Word

```  
[Time1]Lyric1[Time2]Lyric2[Time3]Lyric3[Time4]Lyric4[Time5]  
[Time1]Lyric1<Time2>Lyric2<Time3>Lyric3<Time4>Lyric4<Time5>  
```  

Supports word-by-word timing via multiple `[]` or `<>` tags.

```  
[Time1]v1: <Time1>Lyric1<Time2>Lyric2<Time3>  
```  

Supports marking singers via prefixes like `v1: `.

```  
[Time1]Lyric1[Time2]Lyric2[Time3]  
[Time1]Translation[Time]  
                  (↑ No time restrictions here; 
                  translations will ultimately follow the main lyrics)  
```  

Supports marking translations via the same timeline. Translations do not support word-by-word timing.

For empty time-tag lines, Flamingo displays a countdown to the next lyric line when the gap is long enough.


#### Time Tags

Flamingo supports the following time tag formats:

```  
[mm:ss]  
[mm:ss.ff]  
[mm:ss.fff]  
```  

Note: `mm` can be any integer length. `ss` is parsed as a number, so decimal fractions after `.` are supported. Colon-separated frame formats such as `[mm:ss:ff]` are not supported.


### Effect Support

- [x] Word-by-word highlighting
- [x] Word-by-word ascent
- [x] Duet / alternating singer alignment via singer prefixes
- [x] Translation display via repeated timestamps
- [ ] LRC `[bg: ]` harmony parsing
- [ ] Colon-separated frame time tags such as `[mm:ss:ff]`


## TTML

> TTML (Timed Text Markup Language) is an XML-based timed text markup language used for timed text and subtitles.

> Flamingo supports embedded TTML lyrics and same-base-name sidecar `.ttml` or `.xml` files.

If both TTML and LRC are available for the same track, Flamingo prefers TTML. If TTML parsing fails, Flamingo falls back to LRC when available.


### Parsing Support

#### Line-by-Line

```xml
<p begin="00:00:03.730" end="00:00:08.110">Lyrics</p>
```

Supports line-timed TTML using each `<p>` element's `begin` and `end` attributes.


#### Word-by-Word

```xml
<p begin="1.000" end="2.000">
  <span begin="1.000" end="1.500">Lyric</span>
  <span begin="1.500" end="2.000">text</span>
</p>
```

Supports word-by-word timing from child `<span>` elements. Word highlighting and ascent use the span timing.


#### Subtitles And Transliterations

```xml
<p begin="7.372" end="10.915" itunes:key="L1">Lyrics</p>

<translations>
  <translation type="subtitle" xml:lang="en-US">
    <text for="L1">Subtitle text</text>
  </translation>
</translations>

<transliterations>
  <transliteration xml:lang="ko-Latn">
    <text for="L1">Transliteration text</text>
  </transliteration>
</transliterations>
```

Supports Apple Music-style subtitles and transliterations matched by `itunes:key` / `for` values.

The lyrics page translate toggle controls TTML secondary text:

- Toggle off: shows transliteration.
- Toggle on: shows subtitle / translation.
- If no secondary text exists, the toggle is disabled or hidden.


#### Overlapping Lines

TTML lines with overlapping time ranges are displayed at the same time. All active overlapping lines are focused together, and the lyrics view scrolls to keep the focused group centered. Each active line shows its own secondary text when available.


#### Time Tags

Flamingo supports the following TTML time formats:

```xml
begin="7.372"
begin="1:07.372"
begin="00:01:07.372"
begin="7.372s"
```


#### Metadata

Flamingo reads the lyric body, line timing, word timing, `ttm:agent`, subtitles, and transliterations. Other metadata such as song parts, songwriters, Composer groups, and Composer colors is ignored in the current version.


### Effect Support

- [x] Line-by-line timing
- [x] Word-by-word highlighting
- [x] Word-by-word ascent
- [x] Simultaneous focused overlapping lines
- [x] Subtitle / translation display
- [x] Transliteration display
- [x] Duet / alternating singer alignment via `ttm:agent="v2"`
- [ ] Displaying song part labels such as Verse / Chorus
- [ ] Composer group color rendering


## Other Notes

#### Lyric Formatting

To ensure clean and neat display, Flamingo's lyric parser includes a built-in lyric formatting function. It merges repeated spaces and trims leading spaces from lyric segments. This may result in slight visual differences between the displayed lyrics and the original file content, so please note this.
