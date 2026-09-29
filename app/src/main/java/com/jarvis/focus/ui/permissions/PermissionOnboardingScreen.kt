package com.jarvis.focus.ui.permissions

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jarvis.focus.ui.components.FocusShieldLogo
import com.jarvis.focus.ui.theme.PrimaryFocus
import com.jarvis.focus.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PermissionOnboardingScreen(
    onAllPermissionsGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Permission states
    var usageState by remember { mutableStateOf(PermissionState.REQUIRED) }
    var overlayState by remember { mutableStateOf(PermissionState.REQUIRED) }
    var notificationState by remember { mutableStateOf(PermissionState.REQUIRED) }
    var batteryState by remember { mutableStateOf(PermissionState.REQUIRED) }

    // Success screen trigger
    var showSuccessCelebration by remember { mutableStateOf(false) }

    // Notification permission launcher for Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationState = if (isGranted) PermissionState.ENABLED else PermissionState.RETURNED_NOT_ENABLED
    }

    // Function to check all permissions and update states
    val checkPermissions = rememberUpdatedState {
        val usageGranted = PermissionManager.hasUsageAccess(context)
        val overlayGranted = PermissionManager.hasOverlayPermission(context)
        val notificationGranted = PermissionManager.hasNotificationPermission(context)
        val batteryGranted = PermissionManager.isIgnoringBatteryOptimizations(context)

        usageState = when {
            usageGranted -> PermissionState.ENABLED
            usageState == PermissionState.OPENING -> PermissionState.RETURNED_NOT_ENABLED
            usageState == PermissionState.FAILED_TO_OPEN -> PermissionState.FAILED_TO_OPEN
            else -> PermissionState.REQUIRED
        }

        overlayState = when {
            overlayGranted -> PermissionState.ENABLED
            overlayState == PermissionState.OPENING -> PermissionState.RETURNED_NOT_ENABLED
            overlayState == PermissionState.FAILED_TO_OPEN -> PermissionState.FAILED_TO_OPEN
            else -> PermissionState.REQUIRED
        }

        notificationState = when {
            notificationGranted -> PermissionState.ENABLED
            notificationState == PermissionState.OPENING -> PermissionState.RETURNED_NOT_ENABLED
            notificationState == PermissionState.FAILED_TO_OPEN -> PermissionState.FAILED_TO_OPEN
            else -> PermissionState.REQUIRED
        }

        batteryState = when {
            batteryGranted -> PermissionState.ENABLED
            batteryState == PermissionState.OPENING -> PermissionState.RETURNED_NOT_ENABLED
            batteryState == PermissionState.FAILED_TO_OPEN -> PermissionState.FAILED_TO_OPEN
            else -> PermissionState.REQUIRED
        }
    }

    // Continuous check loop to catch permissions granted in background/split-screen
    LaunchedEffect(Unit) {
        while (true) {
            checkPermissions.value()
            delay(600)
        }
    }

    // Lifecycle observer to immediately detect when user returns from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions.value()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // List of permission cards to display
    val permissionCards = remember(usageState, overlayState, notificationState, batteryState) {
        val list = mutableListOf<PermissionItemData>()

        list.add(
            PermissionItemData(
                type = PermissionType.USAGE_ACCESS,
                title = "Usage Access",
                description = "Required to detect when distracting apps are opened and measure your screen time accurately.",
                isCritical = true,
                state = usageState
            )
        )

        list.add(
            PermissionItemData(
                type = PermissionType.OVERLAY,
                title = "Display Over Other Apps",
                description = "Required to show focus controls and reminder screens when your daily limit is reached.",
                isCritical = true,
                state = overlayState
            )
        )

        // Notification permission is relevant on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(
                PermissionItemData(
                    type = PermissionType.NOTIFICATIONS,
                    title = "Notifications",
                    description = "Required to keep Focus Shield active in the background and show live protection status.",
                    isCritical = true,
                    state = notificationState
                )
            )
        }

        // Battery optimization exemption (recommended)
        list.add(
            PermissionItemData(
                type = PermissionType.BATTERY_OPTIMIZATION,
                title = "Unrestricted Background",
                description = "Recommended to prevent Android from pausing your focus shield when device battery is low.",
                isCritical = false,
                state = batteryState
            )
        )

        list
    }

    val criticalCards = permissionCards.filter { it.isCritical }
    val criticalCompleted = criticalCards.count { it.state == PermissionState.ENABLED }
    val allCriticalGranted = criticalCompleted == criticalCards.size

    // Check if celebration screen should trigger
    LaunchedEffect(allCriticalGranted) {
        if (allCriticalGranted && !showSuccessCelebration) {
            // Short delay to let the user see the final checkmark pop
            delay(400)
            showSuccessCelebration = true
        }
    }

    AnimatedContent(
        targetState = showSuccessCelebration,
        transitionSpec = {
            fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.92f) togetherWith
                    fadeOut(animationSpec = tween(300))
        },
        label = "onboarding_screen_transition"
    ) { isCelebration ->
        if (isCelebration) {
            SuccessCelebrationContent(
                onStartFocusing = onAllPermissionsGranted
            )
        } else {
            OnboardingMainContent(
                cards = permissionCards,
                criticalCompleted = criticalCompleted,
                totalCritical = criticalCards.size,
                allCriticalGranted = allCriticalGranted,
                onEnablePermission = { type ->
                    when (type) {
                        PermissionType.USAGE_ACCESS -> {
                            usageState = PermissionState.OPENING
                            val opened = PermissionManager.openUsageAccessSettings(context)
                            if (!opened) {
                                usageState = PermissionState.FAILED_TO_OPEN
                                Toast.makeText(context, "Could not open Usage settings directly. Please open App Settings.", Toast.LENGTH_LONG).show()
                            }
                        }
                        PermissionType.OVERLAY -> {
                            overlayState = PermissionState.OPENING
                            val opened = PermissionManager.openOverlaySettings(context)
                            if (!opened) {
                                overlayState = PermissionState.FAILED_TO_OPEN
                                Toast.makeText(context, "Could not open Overlay settings directly.", Toast.LENGTH_LONG).show()
                            }
                        }
                        PermissionType.NOTIFICATIONS -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationState = PermissionState.OPENING
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notificationState = PermissionState.OPENING
                                PermissionManager.openNotificationSettings(context)
                            }
                        }
                        PermissionType.BATTERY_OPTIMIZATION -> {
                            batteryState = PermissionState.OPENING
                            val opened = PermissionManager.openBatteryOptimizationSettings(context)
                            if (!opened) {
                                batteryState = PermissionState.FAILED_TO_OPEN
                                Toast.makeText(context, "Could not open Battery settings directly.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                onContinue = {
                    showSuccessCelebration = true
                },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun OnboardingMainContent(
    cards: List<PermissionItemData>,
    criticalCompleted: Int,
    totalCritical: Int,
    allCriticalGranted: Boolean,
    onEnablePermission: (PermissionType) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header section
                item {
                    OnboardingHeader()
                }

                // Progress Bar & Step Counter
                item {
                    ProgressStepIndicator(
                        completed = criticalCompleted,
                        total = totalCritical
                    )
                }

                // Section Title
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Required Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Permission Cards with Staggered Entrance
                itemsIndexed(cards, key = { _, item -> item.type.name }) { index, item ->
                    AnimatedPermissionCard(
                        item = item,
                        index = index,
                        onEnable = { onEnablePermission(item.type) }
                    )
                }

                // Privacy Trust Badge
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    PrivacyGuaranteeCard()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Bottom CTA Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (allCriticalGranted) {
                        Button(
                            onClick = onContinue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryFocus
                            )
                        ) {
                            Text(
                                text = "Continue to Focus Shield",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    } else {
                        Text(
                            text = "Enable the required permissions above to activate Focus Shield",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Modern Theme-Aligned Focus Shield Logo (Shield + Focus Reticle)
        FocusShieldLogo(size = 108.dp)

        Spacer(modifier = Modifier.height(20.dp))

        // App Title
        Text(
            text = "Focus Shield",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Friendly Subtitle
        Text(
            text = "Stay focused. Take control of your screen time.",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "A few permissions are required to activate Focus Shield protection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun ProgressStepIndicator(completed: Int, total: Int) {
    val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "progress_step"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Setup Progress",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$completed of $total required enabled",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (completed == total) SuccessGreen else MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (completed == total) SuccessGreen else PrimaryFocus,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun AnimatedPermissionCard(
    item: PermissionItemData,
    index: Int,
    onEnable: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(index * 90L)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(400)) + slideInVertically(
            initialOffsetY = { 40 },
            animationSpec = tween(400, easing = LinearOutSlowInEasing)
        )
    ) {
        PermissionCard(item = item, onEnable = onEnable)
    }
}

@Composable
private fun PermissionCard(
    item: PermissionItemData,
    onEnable: () -> Unit
) {
    val isEnabled = item.state == PermissionState.ENABLED
    val isOpening = item.state == PermissionState.OPENING
    val isReturnedNotEnabled = item.state == PermissionState.RETURNED_NOT_ENABLED
    val isFailedToOpen = item.state == PermissionState.FAILED_TO_OPEN

    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isEnabled -> SuccessGreen.copy(alpha = 0.5f)
            isReturnedNotEnabled -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        },
        animationSpec = tween(300),
        label = "card_border"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cardBorderColor, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Feature Icon Container
                val icon = getPermissionIcon(item.type)
                val iconTint = if (isEnabled) SuccessGreen else PrimaryFocus
                val iconBg = if (isEnabled) SuccessGreen.copy(alpha = 0.12f) else PrimaryFocus.copy(alpha = 0.10f)

                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = iconBg
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = isEnabled,
                            transitionSpec = {
                                fadeIn(tween(250)) + scaleIn(initialScale = 0.5f) togetherWith
                                        fadeOut(tween(200))
                            },
                            label = "icon_anim"
                        ) { granted ->
                            Icon(
                                imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (!item.isCritical) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "Optional",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action / Status Area
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isEnabled -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SuccessGreen.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Enabled",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                            }
                        }
                    }
                    isOpening -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Opening Settings...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    isReturnedNotEnabled -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Permission required",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Button(
                                onClick = onEnable,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Try Again",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    isFailedToOpen -> {
                        Button(
                            onClick = onEnable,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(
                                text = "Open Settings",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> {
                        Button(
                            onClick = onEnable,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryFocus,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Enable",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyGuaranteeCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Privacy First",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Focus Shield operates locally on your device. Your usage data is never transmitted or shared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun SuccessCelebrationContent(
    onStartFocusing: () -> Unit
) {
    val checkmarkScale = remember { Animatable(0.3f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        checkmarkScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(400)
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Celebratory Checkmark Shield
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(checkmarkScale.value),
                    shape = CircleShape,
                    color = SuccessGreen.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, SuccessGreen.copy(alpha = 0.5f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Success",
                            tint = SuccessGreen,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.scale(contentAlpha.value)
            ) {
                Text(
                    text = "You're all set!",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Focus Shield is now ready to protect your time and help you stay on track.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onStartFocusing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryFocus
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Start Focusing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

private fun getPermissionIcon(type: PermissionType): ImageVector {
    return when (type) {
        PermissionType.USAGE_ACCESS -> Icons.Rounded.Timeline
        PermissionType.OVERLAY -> Icons.Rounded.Layers
        PermissionType.NOTIFICATIONS -> Icons.Rounded.NotificationsActive
        PermissionType.BATTERY_OPTIMIZATION -> Icons.Rounded.BatteryChargingFull
    }
}
