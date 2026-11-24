package com.cpaconceptlab.study.data.importer

import androidx.room.withTransaction
import com.cpaconceptlab.study.data.local.AppDatabase
import com.cpaconceptlab.study.data.local.ConceptProgressEntity
import com.cpaconceptlab.study.data.local.ContentVersionEntity
import com.cpaconceptlab.study.data.local.ImportedPackEntity
import com.cpaconceptlab.study.data.local.QuestionConceptCrossRef
import com.cpaconceptlab.study.data.model.ContentPack
import com.cpaconceptlab.study.domain.model.MasteryLevel

/**
 * 콘텐츠 가져오기(번들 업데이트·사용자 가져오기 공용).
 *
 * 업데이트 정책(idempotent):
 *  - 새 ID: 추가
 *  - 기존 ID + 더 높은 revision: 콘텐츠 필드만 갱신(학습기록은 별도 테이블이라 그대로 유지)
 *  - 기존 ID + 같거나 낮은 revision: 건너뜀(중복 삽입 없음)
 *  - 의미·정답이 바뀌는 수정은 새 questionId 를 발급하고 기존 문항은 isActive=false 로 비활성화한다.
 *  - 대단원·중단원·과목·출제범위 등 구조 데이터는 항상 최신 값으로 upsert 한다.
 *
 * 전체 과정은 하나의 Room 트랜잭션으로 실행되어, 중간에 오류가 나면 기존 데이터가 그대로 남는다.
 */
class ContentImporter(private val db: AppDatabase) {

    data class Result(
        val success: Boolean,
        val plan: PackValidator.Plan?,
        val message: String,
        val insertedConcepts: Int = 0,
        val updatedConcepts: Int = 0,
        val insertedQuestions: Int = 0,
        val updatedQuestions: Int = 0,
    )

    suspend fun import(
        packs: List<ContentPack>,
        source: String,
        packKey: String? = null,
        fileName: String? = null,
        rawJson: String? = null,
        now: Long = System.currentTimeMillis(),
    ): Result {
        val dao = db.contentDao()
        val conceptRev = dao.conceptRevisions().associate { it.id to it.revision }
        val questionRev = dao.questionRevisions().associate { it.id to it.revision }
        val plan = PackValidator.validate(
            packs = packs,
            existingConceptRevisions = conceptRev,
            existingQuestionRevisions = questionRev,
            existingChapterIds = dao.chapters().map { it.id }.toSet(),
            existingSectionIds = dao.sections().map { it.id }.toSet(),
            existingSubjectIds = dao.subjects().map { it.id }.toSet(),
        )
        if (!plan.ok) {
            return Result(false, plan, "검증 오류 ${plan.errors.size}건으로 가져오기를 중단했습니다. 기존 데이터는 변경되지 않았습니다.")
        }

        var insC = 0; var updC = 0; var insQ = 0; var updQ = 0
        db.withTransaction {
            for (p in packs) {
                if (p.subjects.isNotEmpty()) dao.upsertSubjects(p.subjects.map { it.toEntity() })
                if (p.chapters.isNotEmpty()) dao.upsertChapters(p.chapters.map { it.toEntity() })
                if (p.sections.isNotEmpty()) dao.upsertSections(p.sections.map { it.toEntity() })
                if (p.examScopes.isNotEmpty()) dao.upsertExamScopes(p.examScopes.map { it.toEntity() })
                if (p.problemSources.isNotEmpty()) dao.upsertProblemSources(p.problemSources.map { it.toEntity() })
                if (p.scopeItems.isNotEmpty()) dao.upsertScopeItems(p.scopeItems.map { it.toEntity() })
                // v4 기업법 참조 데이터: 법령 개정·커버리지 변화가 반영되도록 항상 최신 값으로 upsert
                if (p.curriculumItems.isNotEmpty()) dao.upsertCurriculumItems(p.curriculumItems.map { it.toEntity() })
                if (p.lawArticles.isNotEmpty()) dao.upsertLawArticles(p.lawArticles.map { it.toEntity() })
                if (p.numericRules.isNotEmpty()) dao.upsertNumericRules(p.numericRules.map { it.toEntity() })
                if (p.caseNotes.isNotEmpty()) dao.upsertCaseNotes(p.caseNotes.map { it.toEntity() })
                if (p.examAreas.isNotEmpty()) dao.upsertExamAreas(p.examAreas.map { it.toEntity() })
                if (p.lawVersions.isNotEmpty()) dao.upsertLawVersions(p.lawVersions.map { it.toEntity() })

                val conceptsToWrite = p.concepts.filter { c ->
                    val old = conceptRev[c.id]
                    (old == null).also { if (it) insC++ } || (old != null && c.revision > old).also { if (it) updC++ }
                }
                if (conceptsToWrite.isNotEmpty()) dao.upsertConcepts(conceptsToWrite.map { it.toEntity(p.contentVersion) })

                val questionsToWrite = p.questions.filter { q ->
                    val old = questionRev[q.id]
                    (old == null).also { if (it) insQ++ } || (old != null && q.revision > old).also { if (it) updQ++ }
                }
                if (questionsToWrite.isNotEmpty()) {
                    val entities = questionsToWrite.map { it.toEntity(p.contentVersion) }
                    dao.upsertQuestions(entities)
                    dao.deleteCrossRefsFor(entities.map { it.id })
                    dao.insertCrossRefs(entities.flatMap { q -> q.conceptIds.map { QuestionConceptCrossRef(q.id, it) } })
                }
            }
            // 새 개념에 대해서만 초기 학습상태 생성(기존 행은 IGNORE 로 보존)
            val newConceptIds = packs.flatMap { it.concepts }.map { it.id }.filter { it !in conceptRev }
            if (newConceptIds.isNotEmpty()) {
                db.progressDao().insertConceptProgressIfAbsent(newConceptIds.map {
                    ConceptProgressEntity(
                        conceptId = it, firstStudiedAt = null, lastStudiedAt = null, viewCount = 0,
                        basicAttemptCount = 0, basicCorrectCount = 0, advancedAttemptCount = 0, advancedCorrectCount = 0,
                        masteryLevel = MasteryLevel.NOT_STARTED.name, nextReviewAt = null, isBookmarked = false,
                    )
                })
            }
            val version = packs.maxOf { it.contentVersion }
            dao.insertContentVersion(
                ContentVersionEntity(
                    version = version, source = source, packId = packs.joinToString(",") { it.packId },
                    importedAt = now, conceptCount = dao.activeConceptCount(), questionCount = dao.activeQuestionCount(),
                    insertedConcepts = insC, updatedConcepts = updC, insertedQuestions = insQ, updatedQuestions = updQ,
                ),
            )
            if (source == SOURCE_IMPORTED && rawJson != null) {
                dao.upsertImportedPack(ImportedPackEntity(packKey ?: packs.joinToString(",") { it.packId }, fileName ?: "", now, rawJson))
            }
        }
        val msg = buildString {
            append("개념 추가 ${insC}개, 갱신 ${updC}개 / 문항 추가 ${insQ}개, 갱신 ${updQ}개")
            val skipped = plan.skippedConcepts + plan.skippedQuestions
            if (skipped > 0) append(" / 중복 건너뜀 ${skipped}개")
        }
        return Result(true, plan, msg, insC, updC, insQ, updQ)
    }

    companion object {
        const val SOURCE_BUNDLED = "bundled"
        const val SOURCE_IMPORTED = "imported"
        const val SOURCE_RESTORED = "restored"
    }
}
