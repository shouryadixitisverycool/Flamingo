package yos.music.player.code

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.kyant.taglib.AudioPropertiesReadStyle
import com.kyant.taglib.TagLib
import java.io.File
import java.nio.charset.Charset

object AudioMetadataUtils {
    private const val tag = "FlamingoLyrics"

    fun loadLrcFile(context: Context, filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(tag, "LRC file missing: $filePath")
                return null
            }
            Log.d(tag, "Reading LRC file: $filePath")
            file.readText(Charset.defaultCharset())
        } catch (e: Exception) {
            Log.e(tag, "Failed reading LRC file: $filePath", e)
            e.printStackTrace()
            null
        }
    }

    fun resolveLocalAudioFilePath(context: Context, uri: Uri?): String? {
        if (uri == null) {
            Log.w(tag, "resolveLocalAudioFilePath called with null uri")
            return null
        }

        if (uri.scheme == "file") {
            Log.d(tag, "Using file uri path: ${uri.path}")
            return uri.path
        }

        uri.path?.let { path ->
            if (File(path).exists()) {
                Log.d(tag, "Using direct uri path: $path")
                return path
            }
        }

        if (uri.scheme != "content") {
            Log.w(tag, "Unsupported uri scheme for lyric lookup: $uri")
            return null
        }

        return try {
            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    Log.w(tag, "MediaStore query returned no rows for uri: $uri")
                    return@use null
                }

                val dataColumnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (dataColumnIndex != -1) {
                    val dataPath = cursor.getString(dataColumnIndex)
                    if (!dataPath.isNullOrBlank() && File(dataPath).exists()) {
                        Log.d(tag, "Resolved via DATA column: $dataPath")
                        return@use dataPath
                    }
                }

                val relativePathColumnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val displayNameColumnIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)

                if (displayNameColumnIndex == -1) {
                    return@use null
                }

                val displayName = cursor.getString(displayNameColumnIndex)
                if (displayName.isNullOrBlank()) {
                    return@use null
                }

                val relativePath = if (relativePathColumnIndex != -1) {
                    cursor.getString(relativePathColumnIndex)
                } else {
                    null
                }

                val externalStorageRoot = Environment.getExternalStorageDirectory()
                val candidateFile = if (relativePath.isNullOrBlank()) {
                    File(externalStorageRoot, displayName)
                } else {
                    File(File(externalStorageRoot, relativePath), displayName)
                }

                if (candidateFile.exists()) {
                    Log.d(tag, "Resolved via RELATIVE_PATH: ${candidateFile.path}")
                    candidateFile.path
                } else {
                    Log.w(
                        tag,
                        "Failed to resolve local path for uri=$uri displayName=$displayName relativePath=$relativePath candidate=${candidateFile.path}"
                    )
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed resolving local path for uri: $uri", e)
            e.printStackTrace()
            null
        }
    }

    fun loadEmbeddedLyrics(filePath: String): String? {
        return try {
            val songFile = File(filePath)
            if (!songFile.exists()) {
                Log.w(tag, "Audio file missing for embedded lyric read: $filePath")
                return null
            }

            ParcelFileDescriptor.open(songFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fileDescriptor ->
                val metadata = TagLib.getMetadata(fileDescriptor.dup().detachFd(), false)
                if (metadata == null) {
                    Log.d(tag, "No embedded metadata returned for: $filePath")
                    return null
                }

                val propertyMap = metadata.propertyMap
                val propertyKeys = propertyMap.keys.sorted()
                Log.d(tag, "Embedded metadata keys for $filePath: $propertyKeys")

                val exactCandidateKeys = listOf(
                    "LYRICS",
                    "LYRICSENG",
                    "UNSYNCEDLYRICS",
                    "SYNCEDLYRICS",
                    "USLT",
                    "SYLT"
                )

                exactCandidateKeys.forEach { key ->
                    val propertyValues = propertyMap[key]
                    val embeddedLyrics = propertyValues
                        ?.firstOrNull { !it.isNullOrBlank() }
                        ?.trim()

                    if (!embeddedLyrics.isNullOrBlank()) {
                        Log.d(tag, "Using embedded lyric key $key for: $filePath")
                        return embeddedLyrics
                    }
                }

                propertyMap.entries.firstOrNull { entry ->
                    entry.key.contains("LYRIC", ignoreCase = true) && entry.value.any { !it.isNullOrBlank() }
                }?.let { entry ->
                    val embeddedLyrics = entry.value.firstOrNull { !it.isNullOrBlank() }?.trim()
                    if (!embeddedLyrics.isNullOrBlank()) {
                        Log.d(tag, "Using fuzzy embedded lyric key ${entry.key} for: $filePath")
                        return embeddedLyrics
                    }
                }

                Log.d(tag, "No embedded lyrics found for: $filePath")
                null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed reading embedded lyrics: $filePath", e)
            null
        }
    }

    fun getQualityInfos(filePath: String): Pair<Int, Int> {
        val songFile = File(filePath)
        var bitrate: Int
        var sampleRate: Int

        println("质量分析 Taglib 实现获取")

        ParcelFileDescriptor.open(songFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            val audioProperties = TagLib.getAudioProperties(fd.dup().detachFd(), AudioPropertiesReadStyle.Fast)
            bitrate = audioProperties?.bitrate ?: -1
            sampleRate = audioProperties?.sampleRate ?: -1
        }

        if (bitrate == -1 || sampleRate == -1) {
            val extractor = MediaExtractor()
            try {
                println("质量分析 MediaExtractor 实现获取")
                extractor.setDataSource(filePath)
                val format = extractor.getTrackFormat(0)
                if (bitrate == -1) {
                    bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                }
                if (sampleRate == -1) {
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                extractor.release()
            }
        }

        return Pair(bitrate, sampleRate)
    }

}
