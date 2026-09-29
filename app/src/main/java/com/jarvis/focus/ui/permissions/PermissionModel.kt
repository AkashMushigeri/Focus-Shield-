package com.jarvis.focus.ui.permissions

enum class PermissionType {
    USAGE_ACCESS,
    OVERLAY,
    NOTIFICATIONS,
    BATTERY_OPTIMIZATION
}

enum class PermissionState {
    NOT_REQUIRED,
    REQUIRED,
    OPENING,
    ENABLED,
    RETURNED_NOT_ENABLED,
    FAILED_TO_OPEN
}

data class PermissionItemData(
    val type: PermissionType,
    val title: String,
    val description: String,
    val isCritical: Boolean = true,
    val state: PermissionState = PermissionState.REQUIRED
)
