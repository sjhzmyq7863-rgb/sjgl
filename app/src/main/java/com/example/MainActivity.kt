package com.example

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Task
import com.example.ui.theme.*
import com.example.viewmodel.TaskViewModel
import com.example.viewmodel.TaskViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppBackground
                ) {
                    TimeManagerApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeManagerApp() {
    val context = LocalContext.current
    val viewModel: TaskViewModel = viewModel(
        factory = TaskViewModelFactory(context.applicationContext as android.app.Application)
    )

    val username by viewModel.username.collectAsStateWithLifecycle()
    val todayTasks by viewModel.todayTasks.collectAsStateWithLifecycle()
    val todayProgress by viewModel.todayProgress.collectAsStateWithLifecycle()
    val weekStats by viewModel.weekFocusStats.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val timerRemainingSeconds by viewModel.timerRemainingSeconds.collectAsStateWithLifecycle()
    val timerDuration by viewModel.timerDurationMinutes.collectAsStateWithLifecycle()
    val timerCategory by viewModel.timerCategory.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showTimerSheet by remember { mutableStateOf(false) }
    var showScheduleSheet by remember { mutableStateOf(false) }

    // App language selection: "简体中文" (Simplified Chinese) and "English"
    var language by remember { mutableStateOf("zh") }

    // Request calendar permissions dynamically
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] ?: false
        if (readGranted && writeGranted) {
            Toast.makeText(context, if (language == "zh") "系统日历读取权限已授予" else "System Calendar permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, if (language == "zh") "日历同步需要日历读写权限" else "Calendar permissions denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Request notification permissions dynamically for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, if (language == "zh") "消息通知与定时提醒已开启" else "Notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, if (language == "zh") "提醒功能需要授予消息通知权限" else "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Check permissions helper
    fun checkAndRequestCalendarPermission(): Boolean {
        val readPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
        val writePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
        val granted = readPerm == PackageManager.PERMISSION_GRANTED && writePerm == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
        return granted
    }

    fun checkAndRequestNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            val granted = perm == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return granted
        }
        return true
    }

    // Dynamic Greeting Translation & Title
    val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greetingText = when {
        language == "zh" -> {
            when {
                currentHour in 5..11 -> "早上好"
                currentHour in 12..17 -> "下午好"
                else -> "晚上好"
            }
        }
        else -> {
            when {
                currentHour in 5..11 -> "Good morning"
                currentHour in 12..17 -> "Good afternoon"
                else -> "Good evening"
            }
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp), // spacing to prevent overlapping action buttons
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header with Clean Minimalism typography, Avatar and Settings action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    val dateFormatted = SimpleDateFormat(
                        if (language == "zh") "M月d日 · EEEE" else "EEE, MMM d",
                        if (language == "zh") Locale.CHINESE else Locale.US
                    ).format(Date())

                    Text(
                        text = dateFormatted.uppercase(Locale.getDefault()),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = "$greetingText, $username!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDominant,
                        lineHeight = 28.sp,
                        modifier = Modifier.clickable { showSettingsDialog = true }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Modern Minimalist settings button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(AppSurface, CircleShape)
                            .border(1.dp, AppBlueBorder, CircleShape)
                            .clickable { showSettingsDialog = true }
                            .testTag("settings_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Avatar placeholder matching mockup style
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(AppBlueLight, CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                            .clip(CircleShape)
                            .clickable { showSettingsDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = username.take(1).uppercase(Locale.getDefault()),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppBlueDarkText
                        )
                    }
                }
            }

            // 2. Goal Completed Box with progress bar (Matches screenshot perfectly)
            Card(
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, AppBlueBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val progressPercent = (todayProgress * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == "zh") "各阶段进度 · $progressPercent%" else "Today's goal: $progressPercent%",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppBluePrimary
                        )
                    }

                    // Animated Flowing Gemini Shimmer for active visual premium feedback
                    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                    val shimmerTranslate by infiniteTransition.animateFloat(
                        initialValue = -800f,
                        targetValue = 1800f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(3200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "shimmer_offset"
                    )
                    
                    val animatedProgressBrush = Brush.horizontalGradient(
                        colors = listOf(
                            AppBluePrimary,
                            Color(0xFF91B4FD),  // Glowing aura color
                            AppBlueLight,
                            Color(0xFF91B4FD),
                            AppBluePrimary
                        ),
                        startX = shimmerTranslate,
                        endX = shimmerTranslate + 400f
                    )

                    // Progress bar matching image styling
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(AppGrayBackground, CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = todayProgress.coerceIn(0.001f, 1f))
                                .fillMaxHeight()
                                .background(
                                    brush = animatedProgressBrush,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }

            // 3. Key Tasks Card
            Card(
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, AppBlueBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == "zh") "关键任务" else "Key Tasks",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDominant
                        )

                        if (selectedFilter != null) {
                            TextButton(onClick = { viewModel.toggleCategoryFilter(null) }) {
                                Text(
                                    text = if (language == "zh") "显示全部" else "Show All",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppBluePrimary
                                )
                            }
                        }
                    }

                    val baseList = todayTasks.filter { it.isKeyTask }
                    val filteredTasks = if (selectedFilter != null) {
                        baseList.filter { it.category == selectedFilter }
                    } else {
                        baseList
                    }

                    if (filteredTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.Assignment,
                                    contentDescription = "Empty",
                                    tint = TextMuted.copy(alpha = 0.4f),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (language == "zh") "今天没有关键任务哦" else "No key tasks today",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            filteredTasks.forEach { task ->
                                KeyTaskRowItem(
                                    task = task,
                                    onCheckToggle = { viewModel.toggleTaskCompletion(task) },
                                    onDelete = { viewModel.deleteTask(task) },
                                    language = language
                                )
                                if (task != filteredTasks.last()) {
                                    HorizontalDivider(
                                        color = AppBlueBorder,
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(start = 36.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Task Category selection Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (language == "zh") "任务分类" else "Task Categories",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val categories = listOf(
                        CategoryDef("工作", Icons.Default.Work, CatWork, CatWorkBg, "Work"),
                        CategoryDef("学习", Icons.Default.School, CatStudy, CatStudyBg, "Study"),
                        CategoryDef("个人", Icons.Default.Person, CatPersonal, CatPersonalBg, "Private"),
                        CategoryDef("健身", Icons.Default.FitnessCenter, CatFitness, CatFitnessBg, "Fitness")
                    )

                    categories.forEach { cat ->
                        val isSelected = selectedFilter == cat.name
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.toggleCategoryFilter(cat.name) }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        if (isSelected) cat.accentColor else cat.bgColor,
                                        RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = cat.icon,
                                    contentDescription = cat.name,
                                    tint = if (isSelected) Color.White else cat.accentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (language == "zh") cat.name else cat.enName,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) cat.accentColor else Color.Black
                            )
                        }
                    }
                }
            }

            // 5. Weekly Focus Statistics (Matches visual look in template)
            Card(
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, AppBlueBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (language == "zh") "本周专注时长统计" else "Focus Statistics this Week",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDominant
                    )

                    val weekDatesAndLabels = viewModel.getWeekDatesAndLabels()
                    val maxMinutes = weekStats.values.maxOrNull()?.coerceAtLeast(60) ?: 120

                    // Vertical list of bars to represent week data
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        weekDatesAndLabels.forEach { (dateStr, label) ->
                            val minutes = weekStats[dateStr] ?: 0
                            val filledFraction = (minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMuted,
                                    modifier = Modifier.width(36.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(10.dp)
                                        .background(AppGrayBackground, CircleShape)
                                ) {
                                    if (minutes > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction = filledFraction)
                                                .fillMaxHeight()
                                                .background(
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(AppBlueLight, AppBluePrimary)
                                                    ),
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }

                                Text(
                                    text = "${minutes}分",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextDominant,
                                    modifier = Modifier
                                        .width(50.dp)
                                        .padding(start = 8.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }

                    // Bottom horizontal label axis matching screenshot
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekDatesAndLabels.forEach { (_, label) ->
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // Floating Action Overlay Bar (Pill buttons matching layout exactly)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.9f))
                    )
                )
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pill: "开始新专注" (Start Focus) with Play Icon
            Button(
                onClick = { showTimerSheet = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppBlueLight,
                    contentColor = AppBlueDarkText
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("start_focus_button"),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = AppBlueDarkText,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (language == "zh") "开始新专注" else "Start New Focus",
                        color = AppBlueDarkText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Right Pill: "查看详细日程" (View schedules list)
            Button(
                onClick = { showScheduleSheet = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppSurface,
                    contentColor = TextDominant
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
                border = BorderStroke(1.dp, AppBlueBorder),
                modifier = Modifier
                    .weight(1.3f)
                    .height(54.dp)
                    .testTag("view_agenda_button"),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = if (language == "zh") "查看详细日程" else "View Detailed Schedule",
                    color = TextDominant,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // settings and translation dialog
    if (showSettingsDialog) {
        var tempName by remember { mutableStateOf(username) }

        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (language == "zh") "应用设置" else "App Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    // Profile User info Edit
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text(if (language == "zh") "用户昵称" else "User Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Language Selector Mode
                    Text(
                        text = if (language == "zh") "语言选项 (Language Option)" else "Language Option",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = language == "zh",
                            onClick = { language = "zh" },
                            label = { Text("简体中文") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = language == "en",
                            onClick = { language = "en" },
                            label = { Text("English") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSettingsDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (language == "zh") "取消" else "Cancel")
                        }

                        Button(
                            onClick = {
                                if (tempName.isNotBlank()) {
                                    viewModel.updateUsername(tempName)
                                }
                                showSettingsDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppBluePrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (language == "zh") "保存" else "Save")
                        }
                    }
                }
            }
        }
    }

    // 6. Interactive Focus Session Timer Bottom/Modal Sheet
    if (showTimerSheet) {
        Dialog(onDismissRequest = {
            // If running, we might prompt, otherwise stop is safe
            if (timerState == TaskViewModel.TimerState.IDLE) {
                showTimerSheet = false
            } else {
                showTimerSheet = false
            }
        }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (language == "zh") "自我专注倒计时" else "Focus Block Timer",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    // Target Category selection for timer
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val simpleCats = listOf("工作", "学习", "个人", "健身")
                        simpleCats.forEach { cat ->
                            val isSel = timerCategory == cat
                            val color = when (cat) {
                                "工作" -> CatWork
                                "学习" -> CatStudy
                                "个人" -> CatPersonal
                                else -> CatFitness
                            }
                            Text(
                                text = cat,
                                color = if (isSel) color else Color.Gray,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) color.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { if (timerState == TaskViewModel.TimerState.IDLE) viewModel.setTimerCategory(cat) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Visual Progress circle with Gemini-like breathing pulsating background light
                    val timerColor = when (timerCategory) {
                        "工作" -> CatWork
                        "学习" -> CatStudy
                        "个人" -> CatPersonal
                        else -> CatFitness
                    }

                    val infiniteTimerTransition = rememberInfiniteTransition(label = "timer_pulse")
                    val breathingAlpha by infiniteTimerTransition.animateFloat(
                        initialValue = 0.05f,
                        targetValue = 0.35f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "aura_alpha"
                    )
                    val breathingScale by infiniteTimerTransition.animateFloat(
                        initialValue = 0.98f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "aura_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pulsating background light representing live/active countdown progression
                        if (timerState == TaskViewModel.TimerState.RUNNING) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(breathingScale)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                timerColor.copy(alpha = breathingAlpha),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }

                        val maxSec = timerDuration * 60
                        val frac = if (maxSec > 0) timerRemainingSeconds.toFloat() / maxSec.toFloat() else 1f
                        CircularProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxSize(),
                            color = timerColor,
                            strokeWidth = 8.dp,
                            trackColor = Color(0xFFF0F0F0)
                        )

                        // Seconds formatting: "mm:ss"
                        val mins = timerRemainingSeconds / 60
                        val secs = timerRemainingSeconds % 60
                        val displayTime = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
                        Text(
                            text = displayTime,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    // Adjustable duration when idle
                    if (timerState == TaskViewModel.TimerState.IDLE) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            listOf(1, 5, 25, 45, 60).forEach { dur ->
                                Button(
                                    onClick = { viewModel.setTimerDuration(dur) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (timerDuration == dur) AppBluePrimary else Color(0xFFF0F0F0)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = "${dur}m",
                                        color = if (timerDuration == dur) Color.White else Color.DarkGray,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (timerState) {
                            TaskViewModel.TimerState.IDLE -> {
                                Button(
                                    onClick = { viewModel.startTimer() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppBluePrimary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, "Start")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (language == "zh") "开始专注" else "Start Block")
                                }
                            }
                            TaskViewModel.TimerState.RUNNING -> {
                                Button(
                                    onClick = { viewModel.pauseTimer() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Pause, "Pause")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (language == "zh") "暂停" else "Pause")
                                }
                            }
                            TaskViewModel.TimerState.PAUSED -> {
                                Button(
                                    onClick = { viewModel.resumeTimer() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppBluePrimary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, "Resume")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (language == "zh") "继续" else "Resume")
                                }
                            }
                        }

                        if (timerState != TaskViewModel.TimerState.IDLE) {
                            OutlinedButton(
                                onClick = { viewModel.stopTimer(saveLog = true) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Stop, "Stop")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (language == "zh") "保存结束" else "Save & End")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showTimerSheet = false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (language == "zh") "关闭" else "Close")
                            }
                        }
                    }
                }
            }
        }
    }

    // 7. Detailed Calendar Schedule view Slide Sheet Dialog
    if (showScheduleSheet) {
        // Collect detailed list
        val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
        val calendarTasks by viewModel.calendarTasks.collectAsStateWithLifecycle()
        val weekDatesAndLabels = viewModel.getWeekDatesAndLabels()

        var showAddTaskDialog by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { showScheduleSheet = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppSurface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.90f)
            ) {
                Scaffold(
                    containerColor = AppSurface,
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                if (checkAndRequestCalendarPermission()) {
                                    // standard check
                                }
                                checkAndRequestNotificationPermission()
                                showAddTaskDialog = true
                            },
                            containerColor = AppBluePrimary,
                            contentColor = Color.White
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add task")
                        }
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == "zh") "详细日程面板" else "Detailed Schedule",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )

                            IconButton(onClick = { showScheduleSheet = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }

                        // Horizontal Dates Selector Ribbon for Monday through Sunday selection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            weekDatesAndLabels.forEach { (dateStr, textLabel) ->
                                val isSelected = dateStr == selectedDate
                                val dayNum = dateStr.substring(8, 10)

                                // Active selection spring micro-animation
                                val dateScale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.05f else 1.0f,
                                    animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                                    label = "date_selection_scale"
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .scale(dateScale)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(
                                            if (isSelected) AppBlueLight else Color.White
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Color.Transparent else AppBlueBorder,
                                            RoundedCornerShape(24.dp)
                                        )
                                        .clickable { viewModel.selectCalendarDate(dateStr) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .widthIn(min = 54.dp)
                                ) {
                                    Text(
                                        text = textLabel,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) AppBlueDarkText else TextMuted
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = dayNum,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AppBlueDarkText else TextDominant
                                    )
                                }
                            }
                        }

                        // Selected Date Header indicator
                        Text(
                            text = if (language == "zh") "日程计划 $selectedDate" else "Schedules for $selectedDate",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.DarkGray
                        )

                        // List of schedules
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (calendarTasks.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 60.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = "Empty",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = if (language == "zh") "今日暂无日程安排" else "No schedule events for this day",
                                                color = Color.Gray,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(calendarTasks) { task ->
                                    CalendarTaskItemRow(
                                        task = task,
                                        onCheckToggle = { viewModel.toggleTaskCompletion(task) },
                                        onDelete = { viewModel.deleteTask(task) },
                                        language = language
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. FAB Dialog to add a custom event to the local app calendar, set reminders, and sync to Android Calendar
        if (showAddTaskDialog) {
            var taskTitle by remember { mutableStateOf("") }
            var taskCategory by remember { mutableStateOf("工作") }
            var startTime by remember { mutableStateOf("09:00") }
            var endTime by remember { mutableStateOf("10:00") }
            var isKeyTask by remember { mutableStateOf(true) }
            var hasReminder by remember { mutableStateOf(false) }
            var syncToDeviceCalendar by remember { mutableStateOf(false) }

            Dialog(onDismissRequest = { showAddTaskDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, AppBlueBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (language == "zh") "新建日程计划" else "Create Schedule Event",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDominant
                        )

                        // Title Text Input
                        OutlinedTextField(
                            value = taskTitle,
                            onValueChange = { taskTitle = it },
                            label = { Text(if (language == "zh") "日程主题" else "Agenda Topic") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppBluePrimary,
                                unfocusedBorderColor = AppBlueBorder,
                                focusedLabelColor = AppBluePrimary,
                                unfocusedLabelColor = TextMuted
                            )
                        )

                        // Category Select Button chips
                        Text(
                            text = if (language == "zh") "选择分类" else "Select Category",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDominant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val categories = listOf("工作", "学习", "个人", "健身")
                            categories.forEach { cat ->
                                val isSel = taskCategory == cat
                                val categoryColor = when (cat) {
                                    "工作" -> CatWork
                                    "学习" -> CatStudy
                                    "个人" -> CatPersonal
                                    else -> CatFitness
                                }
                                val categoryBg = when (cat) {
                                    "工作" -> CatWorkBg
                                    "学习" -> CatStudyBg
                                    "个人" -> CatPersonalBg
                                    else -> CatFitnessBg
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSel) categoryColor else categoryBg)
                                        .border(
                                            1.dp,
                                            if (isSel) Color.Transparent else AppBlueBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { taskCategory = cat }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (language == "zh") cat else {
                                            when (cat) {
                                                "工作" -> "Work"
                                                "学习" -> "Study"
                                                "个人" -> "Private"
                                                else -> "Fitness"
                                            }
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) Color.White else categoryColor
                                    )
                                }
                            }
                        }

                        // Time range Pickers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (language == "zh") "开始时间" else "Start Time",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                OutlinedButton(
                                    onClick = {
                                        showTimePicker(context, startTime) { selectedTime ->
                                            startTime = selectedTime
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, AppBlueBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDominant)
                                ) {
                                    Text(startTime, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (language == "zh") "结束时间" else "End Time",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                OutlinedButton(
                                    onClick = {
                                        showTimePicker(context, endTime) { selectedTime ->
                                            endTime = selectedTime
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, AppBlueBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDominant)
                                ) {
                                    Text(endTime, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Checkboxes and switch selectors
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (language == "zh") "标记为关键任务" else "Promote to Key Tasks",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDominant
                            )
                            Switch(
                                checked = isKeyTask,
                                onCheckedChange = { isKeyTask = it }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (language == "zh") "开启定时提醒通知" else "Add system Alarm Clock notification",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDominant
                            )
                            Switch(
                                checked = hasReminder,
                                onCheckedChange = {
                                    if (it) {
                                        checkAndRequestNotificationPermission()
                                    }
                                    hasReminder = it
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (language == "zh") "同步写入本地系统日历" else "Sync to Android internal Calendar",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDominant
                            )
                            Switch(
                                checked = syncToDeviceCalendar,
                                onCheckedChange = {
                                    if (it) {
                                        checkAndRequestCalendarPermission()
                                    }
                                    syncToDeviceCalendar = it
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showAddTaskDialog = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, AppBlueBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                            ) {
                                Text(if (language == "zh") "取消" else "Cancel", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (taskTitle.isBlank()) {
                                        Toast.makeText(context, if (language == "zh") "主题内容不能为空" else "Topic cannot be empty", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    // Compute exact reminder timestamp if is enabled
                                    val reminderTimeMillis = if (hasReminder) {
                                        com.example.data.CalendarReminderHelper.parseTimeToMillis(selectedDate, startTime) ?: System.currentTimeMillis()
                                    } else {
                                        null
                                    }

                                    viewModel.insertTask(
                                        title = taskTitle,
                                        date = selectedDate,
                                        startTime = startTime,
                                        endTime = endTime,
                                        category = taskCategory,
                                        isKeyTask = isKeyTask,
                                        hasReminder = hasReminder,
                                        reminderTimeMillis = reminderTimeMillis,
                                        addToCalendar = syncToDeviceCalendar
                                    )

                                    Toast.makeText(context, if (language == "zh") "计划添加完成！" else "Added schedule successfully!", Toast.LENGTH_SHORT).show()
                                    showAddTaskDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AppBluePrimary, contentColor = Color.White),
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(if (language == "zh") "添加" else "Add")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeyTaskRowItem(
    task: Task,
    onCheckToggle: () -> Unit,
    onDelete: () -> Unit,
    language: String
) {
    val categoryColor = when (task.category) {
        "工作" -> CatWork
        "学习" -> CatStudy
        "个人" -> CatPersonal
        else -> CatFitness
    }
    val categoryBg = when (task.category) {
        "工作" -> CatWorkBg
        "学习" -> CatStudyBg
        "个人" -> CatPersonalBg
        else -> CatFitnessBg
    }

    // High fidelity tactile bouncy spring animations for checking tasks
    val checkboxScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "key_checkbox_scale"
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 220),
        label = "key_check_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Elegant square checkbox with rounded corners & bouncy tap spring transitions
            Box(
                modifier = Modifier
                    .scale(checkboxScale)
                    .size(24.dp)
                    .border(
                        BorderStroke(
                            2.dp,
                            if (task.isCompleted) categoryColor else TextMuted
                        ),
                        RoundedCornerShape(6.dp)
                    )
                    .background(
                        if (task.isCompleted) categoryColor else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onCheckToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Checked",
                        tint = Color.White,
                        modifier = Modifier
                            .scale(checkAlpha)
                            .size(16.dp)
                    )
                }
            }

            // Title & Subtitle with Clean Minimalism text style
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onCheckToggle() }
            ) {
                Text(
                    text = task.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.isCompleted) TextMuted.copy(alpha = 0.6f) else TextDominant,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Time",
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "${task.startTime} - ${task.endTime}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Action icons and tag indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category Capsule indicator with custom badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(categoryBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (language == "zh") task.category else {
                        when (task.category) {
                            "工作" -> "Work"
                            "学习" -> "Study"
                            "个人" -> "Private"
                            else -> "Fitness"
                        }
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp).testTag("delete_key_task_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.LightGray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun CalendarTaskItemRow(
    task: Task,
    onCheckToggle: () -> Unit,
    onDelete: () -> Unit,
    language: String
) {
    val categoryColor = when (task.category) {
        "工作" -> CatWork
        "学习" -> CatStudy
        "个人" -> CatPersonal
        else -> CatFitness
    }
    val categoryBg = when (task.category) {
        "工作" -> CatWorkBg
        "学习" -> CatStudyBg
        "个人" -> CatPersonalBg
        else -> CatFitnessBg
    }

    val cardBg = if (task.isCompleted) AppSurface else categoryBg
    val cardBorder = if (task.isCompleted) BorderStroke(1.dp, AppBlueBorder) else null

    // Tactile checkoff animations matching KeyTaskRowItem
    val checkboxScale by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cal_checkbox_scale"
    )
    val checkAlpha by animateFloatAsState(
        targetValue = if (task.isCompleted) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 220),
        label = "cal_check_alpha"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(24.dp),
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Elegant square checkbox with rounded corners & bouncy tap spring transitions
                Box(
                    modifier = Modifier
                        .scale(checkboxScale)
                        .size(24.dp)
                        .border(
                            BorderStroke(
                                2.dp,
                                if (task.isCompleted) categoryColor else TextMuted
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .background(
                            if (task.isCompleted) categoryColor else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onCheckToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (task.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier
                                .scale(checkAlpha)
                                .size(16.dp)
                        )
                    }
                }

                // Title & Time info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCheckToggle() }
                ) {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (task.isCompleted) TextMuted.copy(alpha = 0.60f) else TextDominant,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Time duration Indicator",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${task.startTime} - ${task.endTime}",
                            fontSize = 12.sp,
                            color = TextMuted
                        )

                        // Alarm indicator
                        if (task.hasReminder) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = "Alarm clock set",
                                tint = categoryColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        // Calendar indicator
                        if (task.calendarEventId != null) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Calendar Sync",
                                tint = AppBluePrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Short aesthetic visual category chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (language == "zh") task.category else {
                            when (task.category) {
                                "工作" -> "Work"
                                "学习" -> "Study"
                                "个人" -> "Private"
                                else -> "Fitness"
                            }
                        },
                        fontSize = 10.sp,
                        color = categoryColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// TimePickerDialog Helper helper function
private fun showTimePicker(context: Context, currentTime: String, onTimeSelected: (String) -> Unit) {
    val hrs = currentTime.substringBefore(":").toIntOrNull() ?: 9
    val mins = currentTime.substringAfter(":").toIntOrNull() ?: 0

    TimePickerDialog(context, { _, h, m ->
        val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", h, m)
        onTimeSelected(formattedTime)
    }, hrs, mins, true).show()
}

// Simple Structural Definition Helper holding Categories metadata
data class CategoryDef(
    val name: String,
    val icon: ImageVector,
    val accentColor: Color,
    val bgColor: Color,
    val enName: String
)

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

