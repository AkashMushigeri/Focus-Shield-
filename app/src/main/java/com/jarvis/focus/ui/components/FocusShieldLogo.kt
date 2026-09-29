package com.jarvis.focus.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.focus.ui.theme.PrimaryFocus
import com.jarvis.focus.ui.theme.SecondaryFocus

/**
 * Modern Focus Shield Logo that clearly represents Focus & Screen Time Protection,
 * rather than a generic VPN / security padlock.
 * Combines an aerodynamic shield silhouette with a camera focus reticle and central focus point.
 */
@Composable
fun FocusShieldLogo(
    modifier: Modifier = Modifier,
    size: Dp = 104.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")

    val translateY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "float"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val auraScale by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "aura_scale"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    val auraAlpha by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.20f,
            targetValue = 0.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "aura_alpha"
        )
    } else {
        remember { mutableFloatStateOf(0.3f) }
    }

    // Subtle reticle pulse
    val reticlePulse by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "reticle_pulse"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .offset(y = translateY.dp)
    ) {
        // Glowing Background Aura Ring
        Box(
            modifier = Modifier
                .size(size * 0.95f)
                .scale(auraScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PrimaryFocus.copy(alpha = auraAlpha),
                            SecondaryFocus.copy(alpha = auraAlpha * 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Custom Drawn Focus Shield
        Canvas(
            modifier = Modifier.size(size * 0.82f)
        ) {
            val width = this.size.width
            val height = this.size.height

            // 1. Build Shield Path
            val shieldPath = Path().apply {
                val topY = height * 0.08f
                val bottomY = height * 0.92f
                val midX = width * 0.5f

                moveTo(midX, topY)
                // Top-right crown
                cubicTo(
                    width * 0.72f, topY * 0.95f,
                    width * 0.88f, height * 0.14f,
                    width * 0.92f, height * 0.28f
                )
                // Right side body down to bottom tip
                cubicTo(
                    width * 0.92f, height * 0.58f,
                    width * 0.74f, height * 0.78f,
                    midX, bottomY
                )
                // Left side body up from bottom tip
                cubicTo(
                    width * 0.26f, height * 0.78f,
                    width * 0.08f, height * 0.58f,
                    width * 0.08f, height * 0.28f
                )
                // Top-left crown back to top center
                cubicTo(
                    width * 0.12f, height * 0.14f,
                    width * 0.28f, topY * 0.95f,
                    midX, topY
                )
                close()
            }

            // Shield Gradient: Sharp Focus Blue into Cyan/Teal accent
            val shieldGradient = Brush.verticalGradient(
                colors = listOf(
                    PrimaryFocus,
                    Color(0xFF1E42C2),
                    Color(0xFF122C8A)
                ),
                startY = 0f,
                endY = height
            )

            // Draw Filled Shield
            drawPath(
                path = shieldPath,
                brush = shieldGradient
            )

            // Shield Outline Highlight (Modern glass border)
            drawPath(
                path = shieldPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.65f),
                        SecondaryFocus.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // 2. Draw Focus Reticle Elements inside Shield
            val centerX = width * 0.5f
            val centerY = height * 0.46f // Slightly raised center of visual gravity
            val reticleSize = (width * 0.24f) * reticlePulse
            val bracketArm = reticleSize * 0.42f
            val strokeWidth = 2.4.dp.toPx()
            val reticleColor = Color.White
            val accentColor = SecondaryFocus

            // Four Focus Brackets [  ]
            // Top-Left Bracket
            drawLine(
                color = reticleColor,
                start = Offset(centerX - reticleSize, centerY - reticleSize + bracketArm),
                end = Offset(centerX - reticleSize, centerY - reticleSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = reticleColor,
                start = Offset(centerX - reticleSize, centerY - reticleSize),
                end = Offset(centerX - reticleSize + bracketArm, centerY - reticleSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Top-Right Bracket
            drawLine(
                color = reticleColor,
                start = Offset(centerX + reticleSize - bracketArm, centerY - reticleSize),
                end = Offset(centerX + reticleSize, centerY - reticleSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = reticleColor,
                start = Offset(centerX + reticleSize, centerY - reticleSize),
                end = Offset(centerX + reticleSize, centerY - reticleSize + bracketArm),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Bottom-Left Bracket
            drawLine(
                color = reticleColor,
                start = Offset(centerX - reticleSize, centerY + reticleSize - bracketArm),
                end = Offset(centerX - reticleSize, centerY + reticleSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = reticleColor,
                start = Offset(centerX - reticleSize, centerY + reticleSize),
                end = Offset(centerX - reticleSize + bracketArm, centerY + reticleSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Bottom-Right Bracket
            drawLine(
                color = reticleColor,
                start = Offset(centerX + reticleSize - bracketArm, centerY + reticleSize),
                end = Offset(centerX + reticleSize, centerY + reticleSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = reticleColor,
                start = Offset(centerX + reticleSize, centerY + reticleSize),
                end = Offset(centerX + reticleSize, centerY + reticleSize - bracketArm),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Inner Focus Ring (Mindfulness / Focus ring)
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = reticleSize * 0.55f,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1.6.dp.toPx())
            )

            // Center Target Bullseye (Glowing teal/white focus point)
            drawCircle(
                color = accentColor,
                radius = reticleSize * 0.24f,
                center = Offset(centerX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = reticleSize * 0.12f,
                center = Offset(centerX, centerY)
            )

            // Cardinal Time/Focus Notches (12, 3, 6, 9 o'clock subtle ticks)
            val notchOffset = reticleSize * 1.35f
            val notchLength = reticleSize * 0.22f
            val notchColor = accentColor.copy(alpha = 0.8f)
            val notchStroke = 1.8.dp.toPx()

            // Top (12 o'clock)
            drawLine(
                color = notchColor,
                start = Offset(centerX, centerY - notchOffset),
                end = Offset(centerX, centerY - notchOffset + notchLength),
                strokeWidth = notchStroke,
                cap = StrokeCap.Round
            )
            // Bottom (6 o'clock)
            drawLine(
                color = notchColor,
                start = Offset(centerX, centerY + notchOffset - notchLength),
                end = Offset(centerX, centerY + notchOffset),
                strokeWidth = notchStroke,
                cap = StrokeCap.Round
            )
            // Left (9 o'clock)
            drawLine(
                color = notchColor,
                start = Offset(centerX - notchOffset, centerY),
                end = Offset(centerX - notchOffset + notchLength, centerY),
                strokeWidth = notchStroke,
                cap = StrokeCap.Round
            )
            // Right (3 o'clock)
            drawLine(
                color = notchColor,
                start = Offset(centerX + notchOffset - notchLength, centerY),
                end = Offset(centerX + notchOffset, centerY),
                strokeWidth = notchStroke,
                cap = StrokeCap.Round
            )
        }
    }
}
