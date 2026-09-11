package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.data.model.ExtractionStage
import com.example.engine.LiveExtractionUiState
import com.example.ui.AppNavTab

data class NavigationItem(
    val tab: AppNavTab,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun AppBottomBar(
    currentTab: AppNavTab,
    extractionState: LiveExtractionUiState,
    onTabSelected: (AppNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavigationItem(
            tab = AppNavTab.HOME,
            title = "الرئيسية",
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home
        ),
        NavigationItem(
            tab = AppNavTab.EXTRACTION,
            title = "الاستخراج",
            selectedIcon = Icons.Filled.CloudDownload,
            unselectedIcon = Icons.Outlined.CloudDownload
        ),
        NavigationItem(
            tab = AppNavTab.LIBRARY,
            title = "المكتبة",
            selectedIcon = Icons.Filled.Article,
            unselectedIcon = Icons.Outlined.Article
        ),
        NavigationItem(
            tab = AppNavTab.SOURCES_SETTINGS,
            title = "المصادر",
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings
        ),
        NavigationItem(
            tab = AppNavTab.GALLERY,
            title = "الصور",
            selectedIcon = Icons.Filled.PhotoLibrary,
            unselectedIcon = Icons.Outlined.PhotoLibrary
        )
    )

    NavigationBar(
        modifier = modifier.testTag("app_bottom_bar")
    ) {
        items.forEach { item ->
            val isSelected = currentTab == item.tab
            val isExtractingActive = item.tab == AppNavTab.EXTRACTION &&
                    (extractionState.stage == ExtractionStage.EXTRACTING || extractionState.stage == ExtractionStage.ANALYZING)

            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(item.tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (isExtractingActive) {
                                Badge {
                                    Text("${extractionState.progressPercent}%")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title
                        )
                    }
                },
                label = { Text(item.title) },
                modifier = Modifier.testTag("nav_item_${item.tab.name.lowercase()}")
            )
        }
    }
}
