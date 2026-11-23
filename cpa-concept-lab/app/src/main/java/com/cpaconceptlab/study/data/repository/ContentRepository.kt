package com.cpaconceptlab.study.data.repository

import com.cpaconceptlab.study.data.importer.ContentCoverageValidator
import com.cpaconceptlab.study.data.importer.ContentImporter
import com.cpaconceptlab.study.data.importer.ContentParser
import com.cpaconceptlab.study.data.importer.ContentSource
import com.cpaconceptlab.study.data.importer.toDto
import com.cpaconceptlab.study.data.local.AppDatabase
import com.cpaconceptlab.study.data.local.ContentVersionEntity
import com.cpaconceptlab.study.domain.usecase.LawCoverage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ContentRepository(
    private val db: AppDatabase,
    private val bundled: ContentSource,
) {
    private val importer = ContentImporter(db)
    private val _snapshot = MutableStateFlow<ContentSnapshot?>(null)
    val snapshot: StateFlow<ContentSnapshot?> = _snapshot.asStateFlow()

    var requiredConceptIds: List<String> = emptyList()
        private set

    val versions: Flow<List<ContentVersionEntity>> = db.contentDao().contentVersionsFlow()

    /**
     * 앱 시작 시 번들 콘텐츠 동기화.
     * 1) 앱에 포함된 콘텐츠 버전 확인 2) 저장된 콘텐츠 버전 확인
     * 3~6) 새 버전이거나 DB 개수가 부족하면 새 개념·문항·변경 설명만 추가/갱신(idempotent)
     * 7~8) 학습기록 테이블은 건드리지 않고, 새 개념에만 초기 상태 생성
     * 9) 스냅샷을 다시 읽어 전체 학습범위·통계를 재계산
     */
    suspend fun syncBundled(): ContentImporter.Result? {
        val manifest = bundled.manifest()
        requiredConceptIds = manifest.requiredConceptIds
        val dao = db.contentDao()
        val stored = dao.bundledVersion()
        val needs = stored == null || manifest.contentVersion > stored ||
            dao.activeConceptCount() < manifest.conceptCount || dao.activeQuestionCount() < manifest.questionCount
        val result = if (needs) importer.import(bundled.packs(), ContentImporter.SOURCE_BUNDLED) else null
        reload()
        return result
    }

    suspend fun reload() {
        val dao = db.contentDao()
        _snapshot.value = ContentSnapshot(
            subjects = dao.subjects(),
            chapters = dao.chapters(),
            sections = dao.sections(),
            allConcepts = dao.concepts(),
            allQuestions = dao.questions(),
            examScopes = dao.examScopes(),
            bundledVersion = dao.bundledVersion() ?: 0,
            problemSources = dao.problemSources(),
            scopeItems = dao.scopeItems(),
            lawArticles = dao.lawArticles(),
            numericRules = dao.numericRules(),
            caseNotes = dao.caseNotes(),
            examAreas = dao.examAreas(),
            lawVersions = dao.lawVersions(),
        )
    }

    /** 사용자 JSON 콘텐츠 팩 가져오기. 손상·형식 오류는 예외 대신 실패 결과로 반환한다. */
    suspend fun importUserPack(json: String, fileName: String): ContentImporter.Result {
        val pack = try {
            ContentParser.parsePack(json)
        } catch (e: ContentParser.ContentFormatException) {
            return ContentImporter.Result(false, null, e.message ?: "JSON 형식 오류")
        }
        val result = importer.import(listOf(pack), ContentImporter.SOURCE_IMPORTED, packKey = pack.packId, fileName = fileName, rawJson = json)
        if (result.success) reload()
        return result
    }

    /** 전체 백업 복원 시 저장된 사용자 팩 재적용 */
    suspend fun reapplyPacks(rawPacks: List<Pair<String, String>>): List<String> {
        val messages = mutableListOf<String>()
        for ((name, json) in rawPacks) {
            val r = importUserPack(json, name)
            messages += "$name: ${r.message}"
        }
        return messages
    }

    suspend fun importedPacks() = db.contentDao().importedPacks()

    fun validationReport(snapshot: ContentSnapshot): ContentCoverageValidator.Report =
        ContentCoverageValidator.validate(
            concepts = snapshot.allConcepts.map { it.toDto() },
            questions = snapshot.allQuestions.map { it.toDto() },
            examScopes = snapshot.examScopes.map { it.toDto() },
            requiredConceptIds = requiredConceptIds,
            sourceStatuses = snapshot.problemSources.map { it.verificationStatus },
            scopeCoverage = snapshot.scopeItems.count { it.covered } to snapshot.scopeItems.size,
            lawRows = LawCoverage.rows(snapshot.lawArticles),
        )
}
