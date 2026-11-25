package com.cpaconceptlab.study.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Upsert suspend fun upsertSubjects(items: List<SubjectEntity>)
    @Upsert suspend fun upsertChapters(items: List<ChapterEntity>)
    @Upsert suspend fun upsertSections(items: List<SectionEntity>)
    @Upsert suspend fun upsertExamScopes(items: List<ExamScopeEntity>)
    @Upsert suspend fun upsertConcepts(items: List<ConceptEntity>)
    @Upsert suspend fun upsertQuestions(items: List<QuestionEntity>)
    @Upsert suspend fun upsertProblemSources(items: List<ProblemSourceEntity>)
    @Upsert suspend fun upsertScopeItems(items: List<ScopeItemEntity>)

    @Upsert suspend fun upsertCurriculumItems(items: List<CurriculumItemEntity>)
    @Query("SELECT * FROM curriculum_items ORDER BY sortOrder") suspend fun curriculumItems(): List<CurriculumItemEntity>
    @Query("DELETE FROM curriculum_items WHERE id IN (:ids)") suspend fun deleteCurriculumItems(ids: List<String>)
    @Upsert suspend fun upsertLawArticles(items: List<LawArticleEntity>)
    @Upsert suspend fun upsertNumericRules(items: List<NumericRuleEntity>)
    @Upsert suspend fun upsertCaseNotes(items: List<CaseNoteEntity>)
    @Upsert suspend fun upsertExamAreas(items: List<ExamAreaEntity>)
    @Upsert suspend fun upsertLawVersions(items: List<LawVersionEntity>)

    @Query("SELECT * FROM law_articles ORDER BY lawShortName, articleNo, branch") suspend fun lawArticles(): List<LawArticleEntity>
    @Query("SELECT * FROM law_articles WHERE id IN (:ids)") suspend fun lawArticlesByIds(ids: List<String>): List<LawArticleEntity>
    @Query("SELECT * FROM numeric_rules ORDER BY ruleId") suspend fun numericRules(): List<NumericRuleEntity>
    @Query("SELECT * FROM case_notes ORDER BY id") suspend fun caseNotes(): List<CaseNoteEntity>
    @Query("SELECT * FROM exam_areas ORDER BY id") suspend fun examAreas(): List<ExamAreaEntity>
    @Query("SELECT * FROM law_versions ORDER BY lawShortName") suspend fun lawVersions(): List<LawVersionEntity>

    @Query("SELECT * FROM problem_sources ORDER BY sourceId") suspend fun problemSources(): List<ProblemSourceEntity>
    @Query("SELECT * FROM scope_items ORDER BY sortOrder") suspend fun scopeItems(): List<ScopeItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(items: List<QuestionConceptCrossRef>)

    @Query("DELETE FROM question_concepts WHERE questionId IN (:questionIds)")
    suspend fun deleteCrossRefsFor(questionIds: List<String>)

    @Insert suspend fun insertContentVersion(item: ContentVersionEntity): Long

    @Upsert suspend fun upsertImportedPack(item: ImportedPackEntity)

    @Query("SELECT * FROM imported_packs ORDER BY importedAt")
    suspend fun importedPacks(): List<ImportedPackEntity>

    @Query("SELECT * FROM subjects ORDER BY sortOrder") suspend fun subjects(): List<SubjectEntity>
    @Query("SELECT * FROM chapters ORDER BY sortOrder") suspend fun chapters(): List<ChapterEntity>
    @Query("SELECT * FROM sections ORDER BY sortOrder") suspend fun sections(): List<SectionEntity>
    @Query("SELECT * FROM exam_scopes ORDER BY number") suspend fun examScopes(): List<ExamScopeEntity>
    @Query("SELECT * FROM concepts ORDER BY sortOrder") suspend fun concepts(): List<ConceptEntity>
    @Query("SELECT * FROM questions ORDER BY sortOrder") suspend fun questions(): List<QuestionEntity>
    @Query("SELECT * FROM question_concepts") suspend fun crossRefs(): List<QuestionConceptCrossRef>

    @Query("SELECT id, revision FROM concepts") suspend fun conceptRevisions(): List<IdRevision>
    @Query("SELECT id, revision FROM questions") suspend fun questionRevisions(): List<IdRevision>

    @Query("SELECT * FROM content_versions ORDER BY id DESC")
    fun contentVersionsFlow(): Flow<List<ContentVersionEntity>>

    @Query("SELECT * FROM content_versions ORDER BY id DESC")
    suspend fun contentVersions(): List<ContentVersionEntity>

    @Query("SELECT MAX(version) FROM content_versions WHERE source = 'bundled'")
    suspend fun bundledVersion(): Int?

    @Query("SELECT COUNT(*) FROM concepts WHERE isActive = 1") suspend fun activeConceptCount(): Int
    @Query("SELECT COUNT(*) FROM questions WHERE isActive = 1") suspend fun activeQuestionCount(): Int
}

data class IdRevision(val id: String, val revision: Int)
