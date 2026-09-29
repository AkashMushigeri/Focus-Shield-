package com.jarvis.focus

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jarvis.focus.data.DataRepository
import com.jarvis.focus.ui.permissions.PermissionManager
import com.jarvis.focus.ui.permissions.PermissionOnboardingScreen
import com.jarvis.focus.ui.theme.FocusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    private val _totalUsageMs = MutableStateFlow(0L)
    val totalUsageMs = _totalUsageMs.asStateFlow()

    private val _dailyGoalMinutes = MutableStateFlow(120)
    val dailyGoalMinutes = _dailyGoalMinutes.asStateFlow()

    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted = _permissionsGranted.asStateFlow()

    private val _isStrictMode = MutableStateFlow(false)
    val isStrictMode = _isStrictMode.asStateFlow()

    private val _usagePermission = MutableStateFlow(false)
    val usagePermission = _usagePermission.asStateFlow()

    private val _overlayPermission = MutableStateFlow(false)
    val overlayPermission = _overlayPermission.asStateFlow()

    private val _batteryPermission = MutableStateFlow(false)
    val batteryPermission = _batteryPermission.asStateFlow()

    private val _batteryAsked = MutableStateFlow(false)
    val batteryAsked = _batteryAsked.asStateFlow()

    fun updateServiceStatus(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
    }

    fun updateUsage(usageMs: Long) {
        _totalUsageMs.value = usageMs
    }

    fun setDailyGoal(minutes: Int) {
        _dailyGoalMinutes.value = minutes
    }

    fun updatePermissionStatus(usage: Boolean, overlay: Boolean, battery: Boolean, ba: Boolean) {
        _usagePermission.value = usage
        _overlayPermission.value = overlay
        _batteryPermission.value = battery
        _batteryAsked.value = ba
        _permissionsGranted.value = usage && overlay && (battery || ba)
    }

    fun setStrictMode(enabled: Boolean) {
        _isStrictMode.value = enabled
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var repository: DataRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = DataRepository.getInstance(this)

        setContent {
            viewModel = viewModel()
            val isRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
            val usageMs by viewModel.totalUsageMs.collectAsStateWithLifecycle()
            val dailyGoal by viewModel.dailyGoalMinutes.collectAsStateWithLifecycle()
            val permissionsGranted by viewModel.permissionsGranted.collectAsStateWithLifecycle()
            val isStrictMode by viewModel.isStrictMode.collectAsStateWithLifecycle()

            FocusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = permissionsGranted,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith
                                    fadeOut(animationSpec = tween(300))
                        },
                        label = "app_root_transition"
                    ) { hasPermissions ->
                        if (hasPermissions) {
                            MainScreen(
                                isRunning = isRunning,
                                usageMs = usageMs,
                                dailyGoal = dailyGoal,
                                isStrictMode = isStrictMode,
                                onToggleShield = { if (isRunning) stopMonitoring() else startMonitoring() },
                                onViewStatsClick = { viewStats() },
                                onSelectAppsClick = { selectApps() },
                                onUpdateGoal = { viewModel.setDailyGoal(it) },
                                onToggleStrictMode = { 
                                    val newMode = !isStrictMode
                                    viewModel.setStrictMode(newMode)
                                    lifecycleScope.launch { repository.setStrictMode(newMode) }
                                    if (newMode) Toast.makeText(this@MainActivity, "Strict Mode Active!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            PermissionOnboardingScreen(
                                onAllPermissionsGranted = {
                                    lifecycleScope.launch {
                                        repository.setBatteryPermissionAsked(true)
                                    }
                                    refreshPermissions()
                                }
                            )
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    val serviceActive = withContext(Dispatchers.Default) {
                        isServiceRunning(UsageMonitorService::class.java)
                    }
                    
                    viewModel.updateServiceStatus(serviceActive)
                    viewModel.setStrictMode(repository.isStrictMode())
                    
                    refreshPermissions()
                    
                    withContext(Dispatchers.IO) {
                        val monitoredApps = repository.getMonitoredApps()
                        var total = 0L
                        monitoredApps.forEach { pkg ->
                            total += repository.getUsage(pkg)
                        }
                        
                        UsageMonitorService.foregroundStart.forEach { (fgPkg, start) ->
                            if (monitoredApps.contains(fgPkg)) {
                                total += (System.currentTimeMillis() - start)
                            }
                        }
                        viewModel.updateUsage(total)
                    }
                    delay(1000)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized && ::repository.isInitialized) {
            refreshPermissions()
        }
    }

    private fun refreshPermissions() {
        val u = PermissionManager.hasUsageAccess(this)
        val o = PermissionManager.hasOverlayPermission(this)
        val b = PermissionManager.isIgnoringBatteryOptimizations(this)
        val ba = repository.wasBatteryPermissionAsked()
        viewModel.updatePermissionStatus(u, o, b, ba)
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
        } catch (e: Exception) {}
        return false
    }

    private fun startMonitoring() {
        if (!PermissionManager.hasUsageAccess(this) || !PermissionManager.hasOverlayPermission(this)) {
            Toast.makeText(this, "Please grant critical permissions first", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val svcIntent = Intent(this, UsageMonitorService::class.java)
            ContextCompat.startForegroundService(this, svcIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start shield", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopMonitoring() {
        val repository = DataRepository.getInstance(this)
        if (repository.isStrictMode()) {
            Toast.makeText(this, "Strict Mode is Active! Cannot deactivate.", Toast.LENGTH_LONG).show()
            return
        }
        stopService(Intent(this, UsageMonitorService::class.java))
    }

    private fun selectApps() {
        startActivity(Intent(this, AppSelectorActivity::class.java))
    }

    private fun viewStats() {
        val intent = Intent(this, PopupActivity::class.java).apply {
            putExtra("mode", "stats")
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isRunning: Boolean,
    usageMs: Long,
    dailyGoal: Int,
    isStrictMode: Boolean,
    onToggleShield: () -> Unit,
    onViewStatsClick: () -> Unit,
    onSelectAppsClick: () -> Unit,
    onUpdateGoal: (Int) -> Unit,
    onToggleStrictMode: () -> Unit
) {
    val totalMinutes = (usageMs / 60000).toInt()
    var showGoalDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Column {
                        Text("Focus Shield", fontWeight = FontWeight.Black)
                        Text(
                            if (isRunning) "Protection Active" else "Shield Offline",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showGoalDialog = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { UsageOverviewCard(totalMinutes, dailyGoal) }
            item { ShieldToggleCard(isRunning, onToggleShield) }
            item {
                Text("Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        title = "App Limits",
                        subtitle = "Manage Time",
                        icon = Icons.Rounded.Timer,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        onClick = onSelectAppsClick
                    )
                    ActionCard(
                        modifier = Modifier.weight(1f),
                        title = "Insights",
                        subtitle = "View Trends",
                        icon = Icons.Rounded.BarChart,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = onViewStatsClick
                    )
                }
            }
            item { StrictModeCard(isStrictMode, onToggleStrictMode) }
            item { StatusCard() }
        }
    }

    if (showGoalDialog) {
        GoalSetterDialog(
            currentGoal = dailyGoal,
            onDismiss = { showGoalDialog = false },
            onConfirm = { 
                onUpdateGoal(it)
                showGoalDialog = false
            }
        )
    }
}

@Composable
fun StrictModeCard(isActive: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Lock, 
                tint = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Strict Mode", fontWeight = FontWeight.Bold)
                Text(
                    "Cannot deactivate shield while active.", 
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isActive, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
fun ShieldToggleCard(isActive: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isActive) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Rounded.Shield else Icons.Rounded.ShieldMoon,
                    contentDescription = null,
                    tint = if (isActive) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isActive) "Shield Active" else "Shield Deactivated",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isActive) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (isActive) "Tap to pause protection" else "Tap to enable focus mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = Color.White
                )
            )
        }
    }
}

@Composable
fun UsageOverviewCard(currentMinutes: Int, goalMinutes: Int) {
    val progress = (currentMinutes.toFloat() / goalMinutes.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(1200), label = "progress")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Today's Focus", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${currentMinutes}m", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                }
                
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.size(80.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 8.dp,
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(80.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 8.dp,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (progress >= 1f) "Goal reached!" else "You have ${goalMinutes - currentMinutes}m left.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionCard(modifier: Modifier = Modifier, title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.5f)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatusCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, tint = MaterialTheme.colorScheme.primary, contentDescription = null)
            Spacer(modifier = Modifier.width(16.dp))
            Text("Optimized for battery efficiency.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GoalSetterDialog(currentGoal: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var goal by remember { mutableFloatStateOf(currentGoal.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Focus Goal") },
        text = {
            Column {
                Text("Target: ${goal.toInt()} minutes", fontWeight = FontWeight.Bold)
                Slider(value = goal, onValueChange = { goal = it }, valueRange = 30f..480f, steps = 15)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(goal.toInt()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
