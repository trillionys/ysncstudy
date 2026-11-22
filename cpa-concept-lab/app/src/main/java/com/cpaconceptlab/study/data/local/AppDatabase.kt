package com.cpaconceptlab.study.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubjectEntity::class, ChapterEntity::class, SectionEntity::class, ConceptEntity::class,
        QuestionEntity::class, QuestionConceptCrossRef::class, ExamScopeEntity::class,
        ContentVersionEntity::class, ImportedPackEntity::class,
        QuestionAttemptEntity::class, QuestionProgressEntity::class, ConceptProgressEntity::class,
        WrongNoteEntity::class, StudyDayEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun progressDao(): ProgressDao

    companion object {
        const val VERSION = 2
        const val NAME = "cpa_concept_lab.db"

        /**
         * 모든 스키마 변경은 명시적 Migration 으로 제공한다.
         * fallbackToDestructiveMigration 은 사용하지 않는다(학습기록 보존).
         */
        /** v1 → v2: 개념별 개인 메모 컬럼 추가. 기존 학습기록은 그대로 유지된다. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE concept_progress ADD COLUMN memo TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
