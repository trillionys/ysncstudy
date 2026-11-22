package com.cpaconceptlab.study

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.ui.LoadState
import com.cpaconceptlab.study.ui.MainViewModel
import com.cpaconceptlab.study.ui.components.ProgressLine
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.concepts.ConceptScreen
import com.cpaconceptlab.study.ui.home.HomeScreen
import com.cpaconceptlab.study.ui.quiz.QuizScreen
import com.cpaconceptlab.study.ui.quiz.QuizSetupScreen
import com.cpaconceptlab.study.ui.quiz.QuizViewModel
import com.cpaconceptlab.study.ui.review.WrongNotesScreen
import com.cpaconceptlab.study.ui.settings.SettingsScreen
import com.cpaconceptlab.study.ui.settings.ValidationReportScreen
import com.cpaconceptlab.study.ui.statistics.StatsScreen
import com.cpaconceptlab.study.ui.subjects.ExamScopeScreen
import com.cpaconceptlab.study.ui.subjects.SubjectScreen
import com.cpaconceptlab.study.ui.theme.CpaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CpaApplication).container
        setContent {
            val vm: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container) } })
            val settings by vm.settings.collectAsState()
            CpaTheme(themeMode = settings.themeMode, fontScale = settings.fontScale) {
                AppRoot(vm)
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("home", "홈", Icons.Filled.Home),
    Tab("subjects", "과목", Icons.AutoMirrored.Filled.List),
    Tab("wrong", "오답노트", Icons.Filled.Edit),
    Tab("stats", "통계", Icons.Filled.Info),
    Tab("settings", "설정", Icons.Filled.Settings),
)

private val TITLES = mapOf(
    "home" to "CPA Concept Lab", "subjects" to "과목", "wrong" to "오답노트", "stats" to "학습 통계", "settings" to "설정",
    "subject" to "개념 목록", "concept" to "개념 학습", "setup" to "문제 풀이 설정", "quiz" to "빈칸 문제", "exam" to "기출범위별 학습현황",
    "report" to "개념 누락 검증 리포트",
)

fun NavHostController.startQuiz(mode: QuizMode, scope: QuizScope, size: Int) {
    navigate("quiz/${mode.name}/${Uri.encode(scope.encode())}/$size/${System.nanoTime()}")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route?.substringBefore("/")?.substringBefore("?") ?: "home"
    val load by vm.load.collectAsState()
    val dash by vm.dashboard.collectAsState()
    val content by vm.content.collectAsState()
    val settings by vm.settings.collectAsState()
    val message by vm.message.collectAsState()
    val isTab = TABS.any { it.route == route }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TITLES[route] ?: "CPA Concept Lab", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (!isTab) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    // 모든 학습 화면에서 즉시 글자 크기 조절
                    TextButton(onClick = { vm.updateSettings { it.copy(fontScale = (it.fontScale - 0.1f).coerceAtLeast(0.85f)) } }) { Text("가−") }
                    TextButton(onClick = { vm.updateSettings { it.copy(fontScale = (it.fontScale + 0.1f).coerceAtMost(1.5f)) } }) { Text("가+") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            if (isTab) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val d = dash
        when {
            load is LoadState.Failed -> Column(Modifier.padding(padding).padding(24.dp)) { Text((load as LoadState.Failed).message) }
            load is LoadState.Loading || d == null -> Column(
                Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator(); Text("학습 콘텐츠 준비 중…", modifier = Modifier.padding(top = 12.dp)) }
            else -> NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                val quiz: (QuizMode, QuizScope) -> Unit = { m, s -> nav.startQuiz(m, s, settings.sessionSize) }
                val openConcept: (String) -> Unit = { nav.navigate("concept/$it") }
                composable("home") {
                    dash?.let { HomeScreen(it, settings, quiz, { nav.navigate("setup") }, { id -> nav.navigate("subject/$id") }, { nav.navigate("exam") }) }
                }
                composable("subjects") {
                    dash?.let { ds ->
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(ds.content.subjects.sortedBy { it.sortOrder }) { s ->
                                val concepts = ds.content.concepts.filter { it.subjectId == s.id }
                                val enabled = s.isActive && concepts.isNotEmpty()
                                SectionCard(title = s.name, modifier = Modifier.clickable(enabled = enabled) { nav.navigate("subject/${s.id}") }) {
                                    if (enabled) {
                                        val studied = concepts.count { ds.overview.masteryByConcept[it.id]?.name != "NOT_STARTED" }
                                        ProgressLine("대단원 ${ds.content.chaptersOf(s.id).size}개 · 개념 학습", studied, concepts.size)
                                    } else Text("콘텐츠 팩을 가져오면 학습할 수 있습니다.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            item { TextButton(onClick = { nav.navigate("exam") }, modifier = Modifier.fillMaxWidth()) { Text("기출범위별 학습현황 (01~46) 보기") } }
                        }
                    }
                }
                composable("subject/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                    dash?.let { SubjectScreen(it, e.arguments?.getString("id") ?: "FA", openConcept, quiz) }
                }
                composable("concept/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                    dash?.let {
                        ConceptScreen(it, e.arguments?.getString("id") ?: "", vm::markViewed, vm::toggleBookmark, vm::saveMemo, openConcept, quiz)
                    }
                }
                composable("exam") { dash?.let { ExamScopeScreen(it, openConcept, quiz) } }
                composable("wrong") { dash?.let { WrongNotesScreen(it, openConcept, quiz) } }
                composable("stats") { dash?.let { StatsScreen(it, openConcept) } }
                composable("settings") { SettingsScreen(vm) { nav.navigate("report") } }
                composable("report") { ValidationReportScreen(vm) }
                composable("setup") {
                    QuizSetupScreen(content, QuizMode.of(settings.lastMode), QuizScope.decode(settings.lastScope), settings.sessionSize) { m, s, size ->
                        nav.startQuiz(m, s, size)
                    }
                }
                composable(
                    "quiz/{mode}/{scope}/{size}/{nonce}",
                    arguments = listOf(
                        navArgument("mode") { type = NavType.StringType },
                        navArgument("scope") { type = NavType.StringType },
                        navArgument("size") { type = NavType.IntType },
                        navArgument("nonce") { type = NavType.StringType },
                    ),
                ) { e ->
                    val mode = QuizMode.of(e.arguments?.getString("mode"))
                    val scope = QuizScope.decode(Uri.decode(e.arguments?.getString("scope")))
                    val size = e.arguments?.getInt("size") ?: 10
                    val nonce = e.arguments?.getString("nonce") ?: ""
                    val qvm: QuizViewModel = viewModel(
                        key = "quiz-$nonce",
                        factory = viewModelFactory { initializer { QuizViewModel(vm.container, mode, scope, size) } },
                    )
                    QuizScreen(
                        vm = qvm,
                        content = content,
                        onOpenConcept = openConcept,
                        onFinish = { nav.popBackStack("home", inclusive = false) },
                        onRestart = { nav.popBackStack(); nav.startQuiz(mode, scope, size) },
                    )
                }
            }
        }
    }

    message?.let { m ->
        AlertDialog(
            onDismissRequest = vm::consumeMessage,
            confirmButton = { TextButton(onClick = vm::consumeMessage) { Text("확인") } },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(m) } },
        )
    }
}
