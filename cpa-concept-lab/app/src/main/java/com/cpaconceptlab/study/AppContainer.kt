package com.cpaconceptlab.study

import android.content.Context
import com.cpaconceptlab.study.data.backup.BackupManager
import com.cpaconceptlab.study.data.importer.BundledAssetContentSource
import com.cpaconceptlab.study.data.local.AppDatabase
import com.cpaconceptlab.study.data.repository.ContentRepository
import com.cpaconceptlab.study.data.repository.SettingsRepository
import com.cpaconceptlab.study.data.repository.StudyRepository

/** 간단한 수동 의존성 컨테이너(앱 단일 인스턴스). */
class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.build(context)
    val contentRepository = ContentRepository(db, BundledAssetContentSource(context.assets))
    val studyRepository = StudyRepository(db)
    val settingsRepository = SettingsRepository(context)
    val backupManager = BackupManager(db)
}
