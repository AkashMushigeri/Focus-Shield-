package com.jarvis.focus.ui.permissions

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionManager {

    fun isPermissionGranted(context: Context, type: PermissionType): Boolean {
        return when (type) {
            PermissionType.USAGE_ACCESS -> hasUsageAccess(context)
            PermissionType.OVERLAY -> hasOverlayPermission(context)
            PermissionType.NOTIFICATIONS -> hasNotificationPermission(context)
            PermissionType.BATTERY_OPTIMIZATION -> isIgnoringBatteryOptimizations(context)
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val runtimeGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            runtimeGranted && notificationsEnabled
        } else {
            notificationsEnabled
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openPermissionSettings(context: Context, type: PermissionType): Boolean {
        return when (type) {
            PermissionType.USAGE_ACCESS -> openUsageAccessSettings(context)
            PermissionType.OVERLAY -> openOverlaySettings(context)
            PermissionType.NOTIFICATIONS -> openNotificationSettings(context)
            PermissionType.BATTERY_OPTIMIZATION -> openBatteryOptimizationSettings(context)
        }
    }

    fun openUsageAccessSettings(context: Context): Boolean {
        // First try package-specific usage access (supported on Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val directIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            if (safeStartActivity(context, directIntent)) return true
        }

        // Fallback to general usage access settings
        val generalIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        if (safeStartActivity(context, generalIntent)) return true

        // Fallback to application details
        return openAppSettings(context)
    }

    fun openOverlaySettings(context: Context): Boolean {
        val directIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        if (safeStartActivity(context, directIntent)) return true

        val generalIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        if (safeStartActivity(context, generalIntent)) return true

        return openAppSettings(context)
    }

    fun openBatteryOptimizationSettings(context: Context): Boolean {
        // Direct intent requesting ignore battery optimizations dialog
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (safeStartActivity(context, requestIntent)) return true

        // Fallback to battery optimization list
        val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (safeStartActivity(context, listIntent)) return true

        return openAppSettings(context)
    }

    fun openNotificationSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            if (safeStartActivity(context, intent)) return true
        }
        return openAppSettings(context)
    }

    fun openAppSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return safeStartActivity(context, intent)
    }

    private fun safeStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
