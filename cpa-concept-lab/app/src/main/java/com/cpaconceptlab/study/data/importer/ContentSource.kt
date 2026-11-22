package com.cpaconceptlab.study.data.importer

import android.content.res.AssetManager
import com.cpaconceptlab.study.data.model.ContentJson
import com.cpaconceptlab.study.data.model.ContentManifest
import com.cpaconceptlab.study.data.model.ContentPack
import kotlinx.serialization.SerializationException

/**
 * 콘텐츠 공급원 추상화.
 *
 * 최초 버전은 APK 에 포함된 assets(오프라인)만 사용한다.
 * 향후 HTTPS 기반 업데이트를 붙일 때는 이 인터페이스를 구현한 RemoteContentSource 를 추가하고
 * 같은 ContentImporter 로 가져오면 된다(검증·트랜잭션·중복 방지 로직 재사용).
 */
interface ContentSource {
    suspend fun manifest(): ContentManifest
    suspend fun packs(): List<ContentPack>
}

class BundledAssetContentSource(private val assets: AssetManager, private val dir: String = "content") : ContentSource {
    override suspend fun manifest(): ContentManifest =
        ContentParser.parseManifest(assets.open("$dir/manifest.json").bufferedReader().use { it.readText() })

    override suspend fun packs(): List<ContentPack> = manifest().files.map { name ->
        ContentParser.parsePack(assets.open("$dir/$name").bufferedReader().use { it.readText() })
    }
}

/** JSON 파서. 손상·형식 오류는 ContentFormatException 으로 변환되어 앱이 종료되지 않는다. */
object ContentParser {
    class ContentFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun parseManifest(json: String): ContentManifest = wrap { ContentJson.decodeFromString(ContentManifest.serializer(), json) }

    fun parsePack(json: String): ContentPack = wrap { ContentJson.decodeFromString(ContentPack.serializer(), json) }

    private inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: SerializationException) {
        throw ContentFormatException("JSON 구조가 올바르지 않습니다: ${e.message?.take(200)}", e)
    } catch (e: IllegalArgumentException) {
        throw ContentFormatException("JSON 값이 올바르지 않습니다: ${e.message?.take(200)}", e)
    }
}
