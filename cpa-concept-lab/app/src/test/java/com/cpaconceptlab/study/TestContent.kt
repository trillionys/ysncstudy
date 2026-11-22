package com.cpaconceptlab.study

import com.cpaconceptlab.study.data.importer.ContentParser
import com.cpaconceptlab.study.data.importer.toEntity
import com.cpaconceptlab.study.data.model.ContentManifest
import com.cpaconceptlab.study.data.model.ContentPack
import com.cpaconceptlab.study.data.repository.ContentSnapshot
import java.io.File

/** JVM 단위 테스트에서 실제 번들 콘텐츠(app/src/main/assets/content)를 읽는다. */
object TestContent {
    val dir: File by lazy {
        val p = System.getProperty("contentAssetsDir")
        val f = if (p != null) File(p) else File("src/main/assets/content")
        require(f.isDirectory) { "콘텐츠 디렉터리를 찾을 수 없습니다: ${f.absolutePath}" }
        f
    }

    val manifest: ContentManifest by lazy { ContentParser.parseManifest(File(dir, "manifest.json").readText()) }

    val packs: List<ContentPack> by lazy { manifest.files.map { ContentParser.parsePack(File(dir, it).readText()) } }

    val snapshot: ContentSnapshot by lazy {
        val v = manifest.contentVersion
        ContentSnapshot(
            subjects = packs.flatMap { it.subjects }.map { it.toEntity() },
            chapters = packs.flatMap { it.chapters }.map { it.toEntity() },
            sections = packs.flatMap { it.sections }.map { it.toEntity() },
            allConcepts = packs.flatMap { it.concepts }.map { it.toEntity(v) },
            allQuestions = packs.flatMap { it.questions }.map { it.toEntity(v) },
            examScopes = packs.flatMap { it.examScopes }.map { it.toEntity() },
            bundledVersion = v,
        )
    }

    fun reportDir(): File = File(System.getProperty("reportDir") ?: "build/reports/content").apply { mkdirs() }
}
