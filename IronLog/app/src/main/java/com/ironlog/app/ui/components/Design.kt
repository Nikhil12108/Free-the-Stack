package com.ironlog.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironlog.app.theme.EyebrowStyle
import com.ironlog.app.theme.accent
import com.ironlog.app.theme.onAccent

/** Small uppercase, widely tracked label: column heads, stat names, meta lines. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, style: TextStyle = EyebrowStyle) {
  Text(text.uppercase(), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Hairline-bordered pill holding a short tag such as "01" or "Resume". */
@Composable
fun TagPill(text: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
  Box(modifier.border(1.dp, color.copy(alpha = 0.22f), CircleShape).padding(horizontal = 10.dp, vertical = 3.dp)) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.75f), maxLines = 1)
  }
}

/** Round icon well, the design's 48px disc. */
@Composable
fun IconDisc(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, container: Color = LocalContentColor.current.copy(alpha = 0.08f), tint: Color = LocalContentColor.current) {
  Box(modifier.size(size).background(container, CircleShape), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
  }
}

/** Arrow pointing up-right: "opens something". */
@Composable
fun OpenArrow(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, size: Dp = 16.dp) {
  Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = tint, modifier = modifier.size(size).rotate(-45f))
}

/** Filter/toggle pill: outlined when off, solid white (or ink, on paper) when on. */
@Composable
fun SelectChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier, trailingIcon: (@Composable () -> Unit)? = null) {
  val colors = MaterialTheme.colorScheme
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(label, maxLines = 1) },
    trailingIcon = trailingIcon,
    modifier = modifier,
    colors =
      FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = colors.onSurfaceVariant,
        iconColor = colors.onSurfaceVariant,
        selectedContainerColor = colors.primary,
        selectedLabelColor = colors.onPrimary,
        selectedTrailingIconColor = colors.onPrimary,
      ),
    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = colors.outline, selectedBorderColor = Color.Transparent),
  )
}

/** The ember card: one per screen at most, for the thing that matters right now. */
@Composable
fun AccentCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.98f else 1f, tween(300), label = "press")
  val colors = MaterialTheme.colorScheme
  Surface(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().graphicsLayer {
      scaleX = scale
      scaleY = scale
    },
    shape = MaterialTheme.shapes.medium,
    color = colors.accent,
    contentColor = colors.onAccent,
    interactionSource = interaction,
  ) {
    Column(Modifier.padding(24.dp), content = content)
  }
}

/** A 1dp rule in the current content colour, like the cards' bottom lines. */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = LocalContentColor.current.copy(alpha = 0.12f)) {
  Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

private val RevealEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Fades and lifts content into place the first time it is composed. The "shown" flag is saveable,
 * so lazy-list items and back-stack entries don't replay it when they come back.
 */
fun Modifier.reveal(delayMillis: Int = 0): Modifier = composed {
  var shown by rememberSaveable { mutableStateOf(false) }
  val progress = remember { Animatable(if (shown) 1f else 0f) }
  LaunchedEffect(Unit) {
    if (!shown) {
      progress.animateTo(1f, tween(durationMillis = 450, delayMillis = delayMillis, easing = RevealEasing))
      shown = true
    }
  }
  graphicsLayer {
    alpha = progress.value
    translationY = (1f - progress.value) * 20.dp.toPx()
  }
}

/** Stagger for the first screenful of a list; later items reveal without waiting. */
fun stagger(index: Int, stepMillis: Int = 45) = index.coerceAtMost(6) * stepMillis

private val noise: ImageBitmap by lazy {
  val size = 160
  val random = java.util.Random(11)
  val pixels = IntArray(size * size) { val v = random.nextInt(256); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
  Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Film grain over everything, overlay-blended: invisible on the void, a faint tooth on light and ember areas. */
fun Modifier.grain(alpha: Float = 0.07f): Modifier = drawWithCache {
  val brush = ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated))
  onDrawWithContent {
    drawContent()
    drawRect(brush, alpha = alpha, blendMode = BlendMode.Overlay)
  }
}
