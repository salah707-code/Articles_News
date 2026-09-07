package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppNavTab
import com.example.ui.NewsViewModel
import com.example.ui.components.*
import com.example.ui.screens.*
import com.example.ui.theme.NewsExtractorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: NewsViewModel = viewModel()
            val userDarkThemeSetting by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val effectiveDarkTheme = userDarkThemeSetting ?: systemDark

            NewsExtractorTheme(darkTheme = effectiveDarkTheme) {
                // Mandatory RTL Layout Direction for Arabic UI
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    NewsExtractorApp(viewModel = viewModel, isDarkTheme = effectiveDarkTheme)
                }
            }
        }
    }
}

@Composable
fun NewsExtractorApp(
    viewModel: NewsViewModel,
    isDarkTheme: Boolean
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val extractionState by viewModel.extractionState.collectAsStateWithLifecycle()
    val allArticles by viewModel.allArticles.collectAsStateWithLifecycle()
    val filteredArticles by viewModel.filteredArticles.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val recentExtractions by viewModel.recentExtractions.collectAsStateWithLifecycle()
    val allGalleryImages by viewModel.allGalleryImages.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val selectedArticle by viewModel.selectedArticle.collectAsStateWithLifecycle()
    val selectedImage by viewModel.selectedImage.collectAsStateWithLifecycle()

    var showScenariosSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                isDarkTheme = isDarkTheme,
                onToggleTheme = { viewModel.toggleTheme() },
                onOpenScenarios = { showScenariosSheet = true }
            )
        },
        bottomBar = {
            AppBottomBar(
                currentTab = currentTab,
                extractionState = extractionState,
                onTabSelected = { tab -> viewModel.setTab(tab) }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Background Extraction Floating Banner when user is not on the extraction tab
            if (currentTab != AppNavTab.EXTRACTION) {
                BackgroundExtractionBanner(
                    state = extractionState,
                    onReturnToExtraction = { viewModel.setTab(AppNavTab.EXTRACTION) }
                )
            }

            // Screen Content
            Crossfade(
                targetState = currentTab,
                label = "MainTabsNavigation",
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    AppNavTab.HOME -> {
                        HomeScreen(
                            stats = stats,
                            recentExtractions = recentExtractions,
                            onStartExtraction = { url -> viewModel.startExtraction(url) },
                            onViewLibrary = { viewModel.setTab(AppNavTab.LIBRARY) }
                        )
                    }

                    AppNavTab.EXTRACTION -> {
                        ExtractionScreen(
                            state = extractionState,
                            onPause = { viewModel.pauseExtraction() },
                            onResume = { viewModel.resumeExtraction() },
                            onCancel = { viewModel.cancelExtraction() },
                            onContinueWithoutImages = { viewModel.continueWithoutImages() },
                            onRetryFailed = { viewModel.retryFailedArticles() },
                            onRetrySingleArticle = { id -> viewModel.retrySingleArticle(id) },
                            onSaveAll = { viewModel.saveCompletedSessionToLibrary() },
                            onViewArticles = { viewModel.setTab(AppNavTab.LIBRARY) },
                            onSelectArticle = { article -> viewModel.selectArticle(article) },
                            onNewExtraction = { viewModel.setTab(AppNavTab.HOME) }
                        )
                    }

                    AppNavTab.LIBRARY -> {
                        LibraryScreen(
                            articles = filteredArticles,
                            stats = stats,
                            filterState = filterState,
                            onSearchChange = { query -> viewModel.updateSearchQuery(query) },
                            onSourceFilterChange = { src -> viewModel.updateSourceFilter(src) },
                            onCategoryFilterChange = { cat -> viewModel.updateCategoryFilter(cat) },
                            onStatusFilterChange = { status -> viewModel.updateStatusFilter(status) },
                            onClearFilters = { viewModel.clearFilters() },
                            onSelectArticle = { article -> viewModel.selectArticle(article) },
                            onDeleteArticle = { id -> viewModel.deleteArticle(id) }
                        )
                    }

                    AppNavTab.GALLERY -> {
                        GalleryScreen(
                            images = allGalleryImages,
                            onSelectImage = { img -> viewModel.selectImage(img) }
                        )
                    }
                }
            }
        }
    }

    // Scenarios Sheet
    if (showScenariosSheet) {
        ScenariosSheet(
            onDismiss = { showScenariosSheet = false },
            onSelectScenario = { url, scenarioKey ->
                viewModel.startExtraction(url, scenarioKey)
            }
        )
    }

    // Article Detail Sheet
    selectedArticle?.let { article ->
        ArticleDetailSheet(
            article = article,
            onDismiss = { viewModel.selectArticle(null) },
            onRetry = { art -> viewModel.retryArticleFromLibrary(art) },
            onDelete = { id -> viewModel.deleteArticle(id) }
        )
    }

    // Image Detail Sheet
    selectedImage?.let { img ->
        ImageDetailSheet(
            image = img,
            onDismiss = { viewModel.selectImage(null) },
            onOpenParentArticle = { artId ->
                val parent = allArticles.find { it.id == artId }
                if (parent != null) {
                    viewModel.selectArticle(parent)
                }
            }
        )
    }
}
