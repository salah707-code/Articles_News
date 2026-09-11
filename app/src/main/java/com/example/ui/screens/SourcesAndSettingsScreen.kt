package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.CustomNewsSource
import com.example.data.model.UserSettings
import com.example.ui.theme.ColorSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesAndSettingsScreen(
    sources: List<CustomNewsSource>,
    userSettings: UserSettings,
    offlineArticlesCount: Int,
    onAddSource: (name: String, url: String, category: String) -> Unit,
    onToggleSource: (CustomNewsSource, Boolean) -> Unit,
    onDeleteSource: (Long) -> Unit,
    onUpdateSettings: (UserSettings) -> Unit,
    onTriggerTestSync: () -> Unit,
    onNavigateToOfflineArticles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var newSourceName by remember { mutableStateOf("") }
    var newSourceUrl by remember { mutableStateOf("") }
    var newSourceCategory by remember { mutableStateOf("عام") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "تم منح إذن الإشعارات بنجاح", Toast.LENGTH_SHORT).show()
            onUpdateSettings(userSettings.copy(notificationsEnabled = true))
        } else {
            Toast.makeText(context, "لم يتم منح إذن الإشعارات", Toast.LENGTH_SHORT).show()
            onUpdateSettings(userSettings.copy(notificationsEnabled = false))
        }
    }

    val availableCategories = listOf("تكنولوجيا", "سياسة", "اقتصاد", "رياضة", "علوم", "صحة", "ثقافة", "عام")
    val frequencyOptions = listOf(
        Pair(15L, "15 دقيقة"),
        Pair(30L, "30 دقيقة"),
        Pair(60L, "ساعة واحدة"),
        Pair(180L, "3 ساعات"),
        Pair(360L, "6 ساعات")
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("sources_and_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
    ) {
        // Section 1: Optional News Sources (إدارة المواقع الإخبارية الاختيارية)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("sources_management_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "مواقع الأخبار الاختيارية",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "تفعيل أو إضافة مصادر ويب وخلاصات RSS",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Button(
                            onClick = { showAddSourceDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("add_source_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("إضافة موقع")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (sources.isEmpty()) {
                        Text(
                            text = "لا توجد مصادر حالياً. اضغط على 'إضافة موقع' لإضافة موقعك الإخباري المفضل.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sources.forEach { source ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (source.isEnabled) {
                                            MaterialTheme.colorScheme.surface
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("source_item_${source.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = source.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (source.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = source.category,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (source.isCustom) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "مخصص",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.tertiary,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = source.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                maxLines = 1
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (source.isCustom) {
                                                IconButton(
                                                    onClick = { onDeleteSource(source.id) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "حذف المصدر",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            Switch(
                                                checked = source.isEnabled,
                                                onCheckedChange = { checked ->
                                                    onToggleSource(source, checked)
                                                },
                                                modifier = Modifier.testTag("switch_source_${source.id}")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Background Notifications & WorkManager (إشعارات الخلفية)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("notifications_settings_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تنبيهات الأخبار في الخلفية",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "جلب الأخبار الجديدة تلقائياً وتنبيهك عند توفر مقالات مطابقة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Switch(
                            checked = userSettings.notificationsEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (!hasPermission) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        return@Switch
                                    }
                                }
                                onUpdateSettings(userSettings.copy(notificationsEnabled = isChecked))
                            },
                            modifier = Modifier.testTag("switch_notifications_enabled")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Preferred Categories (الفئات المفضلة للتنبيهات)
                    Text(
                        text = "الفئات المفضلة للتنبيهات الفورية:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableCategories) { cat ->
                            val isSelected = userSettings.preferredCategories.contains(cat)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val updatedCategories = if (isSelected) {
                                        userSettings.preferredCategories.filter { it != cat }
                                    } else {
                                        userSettings.preferredCategories + cat
                                    }
                                    onUpdateSettings(userSettings.copy(preferredCategories = updatedCategories))
                                },
                                label = { Text(cat) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Sync Frequency (معدل المزامنة)
                    Text(
                        text = "تكرار الفحص في الخلفية:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(frequencyOptions) { (mins, label) ->
                            val isSelected = userSettings.notificationFrequencyMinutes == mins
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onUpdateSettings(userSettings.copy(notificationFrequencyMinutes = mins))
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Immediate Test Trigger Button
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@OutlinedButton
                                }
                            }
                            onTriggerTestSync()
                            Toast.makeText(context, "تم بدء المزامنة وفحص الأخبار في الخلفية الآن...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("trigger_test_sync_button")
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("فحص الأخبار وإرسال إشعار تجريبي الآن")
                    }
                }
            }
        }

        // Section 3: Offline Storage & Sync (حفظ أوفلاين)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("offline_settings_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "حفظ الأخبار أوفلاين (Offline)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "إتاحة تصفح المقالات والصور دون الحاجة لاتصال بالإنترنت",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorSuccess.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$offlineArticlesCount مقال محفوظ",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = ColorSuccess,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الحفظ التلقائي في وضع أوفلاين",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "حفظ كل مقال يتم استخراجه تلقائياً في قاعدة البيانات للقراءة أوفلاين",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Switch(
                            checked = userSettings.autoSaveOffline,
                            onCheckedChange = { checked ->
                                onUpdateSettings(userSettings.copy(autoSaveOffline = checked))
                            },
                            modifier = Modifier.testTag("switch_auto_save_offline")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onNavigateToOfflineArticles,
                        modifier = Modifier.fillMaxWidth().testTag("view_offline_articles_button")
                    ) {
                        Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("عرض المقالات المحفوظة أوفلاين")
                    }
                }
            }
        }
    }

    // Add Source Dialog
    if (showAddSourceDialog) {
        AlertDialog(
            onDismissRequest = { showAddSourceDialog = false },
            title = {
                Text("إضافة موقع إخباري اختياري", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "أدخل اسم الموقع ورابط صفحته الرئيسية أو خلاصة الـ RSS الخاصة به:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedTextField(
                        value = newSourceName,
                        onValueChange = { newSourceName = it },
                        label = { Text("اسم الموقع (مثلاً: رويترز، الحدث، أرقام)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_source_name_input")
                    )

                    OutlinedTextField(
                        value = newSourceUrl,
                        onValueChange = { newSourceUrl = it },
                        label = { Text("رابط الموقع أو الـ RSS (مثلاً: https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_source_url_input")
                    )

                    Text(
                        "التصنيف الافتراضي:",
                        style = MaterialTheme.typography.labelMedium
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableCategories) { cat ->
                            FilterChip(
                                selected = newSourceCategory == cat,
                                onClick = { newSourceCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSourceName.isNotBlank() && newSourceUrl.isNotBlank()) {
                            onAddSource(newSourceName, newSourceUrl, newSourceCategory)
                            newSourceName = ""
                            newSourceUrl = ""
                            showAddSourceDialog = false
                            Toast.makeText(context, "تمت إضافة الموقع بنجاح", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "يرجى تعبئة الاسم والرابط", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_source_button")
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSourceDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
