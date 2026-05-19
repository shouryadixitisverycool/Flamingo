package yos.music.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.funny.data_saver.core.DataSaverConverter.registerTypeConverters
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import yos.music.player.code.MediaController.mediaControl
import yos.music.player.code.MediaController.musicPlaying
import yos.music.player.code.MediaController.playingMusicList
import yos.music.player.code.YosPlaybackService
import yos.music.player.data.libraries.Folder
import yos.music.player.data.libraries.MusicLibrary
import yos.music.player.data.libraries.PlayList
import yos.music.player.data.libraries.YosMediaItem
import yos.music.player.data.libraries.YosStringWrapper
import kotlin.system.exitProcess

class YosBasicApplication : Application() {
    override fun onCreate() {

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            e.printStackTrace()
            CrashActivity.startActivity(this, e.stackTraceToString())
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }

        // 初始化 MMKV
        MMKV.initialize(this)

        val gson =
            GsonBuilder()
            //.registerTypeAdapter(Uri::class.java, UriSerializer())
            //registerTypeAdapter(Uri::class.java, UriDeserializer())
            .registerTypeAdapter(Uri::class.java, UriTypeAdapter())
            .create()

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, Folder::class.java) }
        )

        /*registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, ImmutableList::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, ArrayList::class.java) }
        )*/

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, PlayList::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, IntArray::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, YosMediaItem::class.java) }
        )

        registerTypeConverters(
            save = { bean -> gson.toJson(bean) },
            restore = { str -> gson.fromJson(str, YosStringWrapper::class.java) }
        )

        // 初始化媒体控制器
        val sessionToken = SessionToken(this, ComponentName(this, YosPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaControl = controllerFuture.get()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val playListData = MusicLibrary.loadPlayList()
                        val playStatusData = MusicLibrary.loadPlayStatus()

                        println("prepare 读取历史")
                        if (playListData.mainMusicList != null) {
                            println("prepare 准备调用")
                            /*launch {
                                runCatching {
                                    // 无论设置与否，先还原
                                    val thisMainMusicList = playListData.mainMusicList
                                    mainMusicList.value = thisMainMusicList
                                    println("prepare 恢复主歌单")
                                }
                            }*/

                            if (playListData.musicPlaying != null) {
                                yos.music.player.code.MediaController.restoreQueueState(
                                    playListData.musicPlaying,
                                    playListData.playingMusicList ?: emptyList(),
                                    playListData.historyMusicList ?: emptyList(),
                                    playStatusData.position,
                                    playListData.shuffleModeEnabled,
                                    playStatusData.repeatMode,
                                    false
                                )
                            } else if (playStatusData.music != null) {
                                yos.music.player.code.MediaController.prepare(
                                    playStatusData.music,
                                    playListData.playingMusicList!!,
                                    playStatusData.position,
                                    playStatusData.shuffleModeEnabled,
                                    playStatusData.repeatMode,
                                    false
                                )
                            }

                            if (playListData.playingMusicList != null) {
                                playingMusicList.value = playListData.playingMusicList
                            }

                            if (playListData.musicPlaying != null) {
                                musicPlaying.value = playListData.musicPlaying
                            }
                        }
                    } catch (e:Exception) {
                        e.printStackTrace()
                    }
                }
            },
            MoreExecutors.directExecutor()
        )

        super.onCreate()
    }
}


/*
class ImmutableListTypeAdapter<T> : JsonSerializer<ImmutableList<T>>,
    JsonDeserializer<ImmutableList<T>> {
    override fun serialize(src: ImmutableList<T>?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        return context?.serialize(src?.toList()) ?: JsonNull.INSTANCE
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ImmutableList<T> {
        val listType = object : TypeToken<List<T>>() {}.type
        val list = context?.deserialize<List<T>>(json, listType)
        return ImmutableList.copyOf(list)
    }
}*/

class UriTypeAdapter : TypeAdapter<Uri>() {
    override fun write(out: JsonWriter, value: Uri?) {
        out.value(value.toString())
    }

    override fun read(`in`: JsonReader): Uri {
        return Uri.parse(`in`.nextString())
    }
}

/*
class UriSerializer : JsonSerializer<Uri> {
    override fun serialize(src: Uri?, typeOfSrc: Type?, context: com.google.gson.JsonSerializationContext?): JsonElement {
        return JsonPrimitive(src.toString())
    }
}

class UriDeserializer : JsonDeserializer<Uri> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: com.google.gson.JsonDeserializationContext?): Uri {
        return Uri.parse(json?.asString)
    }
}*/
