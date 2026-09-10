package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ScenarioOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val scenarioKey: String?,
    val defaultUrl: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenariosSheet(
    onDismiss: () -> Unit,
    onSelectScenario: (url: String, scenarioKey: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scenarios = listOf(
        ScenarioOption(
            title = "تدقيق منع التكرار وتصنيف الأخبار القديمة",
            description = "محاكاة استخراج مقالات مع تواريخ متفاوتة وفحص عدم وسم الأخبار الأرشيفية أو المتكررة كجديدة",
            icon = Icons.Default.VerifiedUser,
            scenarioKey = null,
            defaultUrl = "https://www.aljazeera.net/news"
        ),
        ScenarioOption(
            title = "استخراج طبيعي متكامل (تحليل + استخراج + نجاح)",
            description = "عرض المراحل الخمس لتحليل الموقع، ثم عداد الاستخراج الحي، ثم ملخص النجاح الشامل",
            icon = Icons.Default.PlayArrow,
            scenarioKey = null,
            defaultUrl = "https://www.aljazeera.net/news"
        ),
        ScenarioOption(
            title = "حالة انقطاع الإنترنت والاستعادة",
            description = "محاكاة انقطاع الاتصال وحفظ العناصر المستخرجة ثم استئناف العملية بنجاح",
            icon = Icons.Default.WifiOff,
            scenarioKey = "INTERNET_LOST",
            defaultUrl = "https://www.bbc.com/arabic"
        ),
        ScenarioOption(
            title = "حالة انخفاض مساحة التخزين",
            description = "تحذير امتلاء الذاكرة وعرض الحجم المطلوب وخيار المتابعة بدون الصور",
            icon = Icons.Default.SdStorage,
            scenarioKey = "STORAGE_LOW",
            defaultUrl = "https://www.skynewsarabia.com"
        ),
        ScenarioOption(
            title = "حالة فشل مقال وإعادة المحاولة",
            description = "تجربة عدم توقف العملية عند فشل مقال محدد وإتاحة إعادة محاولته لاحقاً",
            icon = Icons.Default.ReportProblem,
            scenarioKey = "ARTICLE_FAIL",
            defaultUrl = "https://bloomberg-arabic.com"
        ),
        ScenarioOption(
            title = "حالة لم نجد مقالات في الصفحة",
            description = "رسالة عدم العثور على مقالات قابلة للاستخراج مع عدد الصفحات المفحوصة",
            icon = Icons.Default.SearchOff,
            scenarioKey = "ERROR_NO_ARTICLES",
            defaultUrl = "https://empty-news-page.com"
        ),
        ScenarioOption(
            title = "حالة تعذر الوصول للموقع",
            description = "رسالة التحقق من الاتصال بالإنترنت وزر إعادة المحاولة",
            icon = Icons.Default.CloudOff,
            scenarioKey = "ERROR_CONNECTION",
            defaultUrl = "https://unreachable-server.net"
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier.testTag("scenarios_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "لوحة استعراض الحالات الديناميكية",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "اختر أي سيناريو لاختبار ومعاينة واجهات التطبيق والمتغيرات الحية فوراً",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(scenarios.size) { idx ->
                    val item = scenarios[idx]
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectScenario(item.defaultUrl, item.scenarioKey)
                                onDismiss()
                            }
                            .testTag("scenario_item_$idx")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
