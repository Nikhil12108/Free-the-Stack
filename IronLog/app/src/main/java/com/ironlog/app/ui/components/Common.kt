package com.ironlog.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.AppContainer
import com.ironlog.app.data.Settings
import com.ironlog.app.domain.Units
import com.ironlog.app.theme.accent
import com.ironlog.app.theme.cardBorder
import kotlin.math.abs

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalSettings = compositionLocalOf { Settings() }

/**
 * Screen scaffold; window insets are handled by the top bar and the app-level bottom bar.
 * [large] gives tab roots a big serif title that tightens into a slim bar as the list scrolls.
 */
@Composable
fun AppScaffold(
  title: String,
  onBack: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
  floatingActionButton: @Composable () -> Unit = {},
  bottomBar: @Composable () -> Unit = {},
  snackbarHostState: SnackbarHostState? = null,
  large: Boolean = false,
  titleStyle: TextStyle? = null,
  content: @Composable (PaddingValues) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val behavior = if (large) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
  val barColors =
    TopAppBarDefaults.topAppBarColors(
      containerColor = colors.background,
      scrolledContainerColor = colors.surfaceContainerLowest,
      navigationIconContentColor = colors.onBackground,
      titleContentColor = colors.onBackground,
      actionIconContentColor = colors.onSurfaceVariant,
    )
  // Hairline under the bar once content slides beneath it.
  val hairline =
    Modifier.drawWithContent {
      drawContent()
      val s = behavior.state
      if (s.overlappedFraction > 0.01f || (large && s.collapsedFraction > 0.99f)) {
        drawLine(colors.outlineVariant, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), strokeWidth = 1.dp.toPx())
      }
    }
  val titleText: @Composable () -> Unit = {
    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = titleStyle ?: LocalTextStyle.current)
  }
  val navIcon: @Composable () -> Unit = {
    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
  }
  Scaffold(
    modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
    topBar = {
      if (large) {
        LargeTopAppBar(title = titleText, navigationIcon = navIcon, actions = actions, colors = barColors, scrollBehavior = behavior, modifier = hairline)
      } else {
        TopAppBar(title = titleText, navigationIcon = navIcon, actions = actions, colors = barColors, scrollBehavior = behavior, modifier = hairline)
      }
    },
    floatingActionButton = floatingActionButton,
    bottomBar = bottomBar,
    snackbarHost = { snackbarHostState?.let { SnackbarHost(it) } },
    containerColor = colors.background,
    contentWindowInsets = WindowInsets(0),
    content = content,
  )
}

/** Serif section title with an optional trailing slot (actions or an eyebrow count). */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
  Row(modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    action?.invoke()
  }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
  Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    action?.let {
      Spacer(Modifier.height(4.dp))
      it()
    }
  }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Eyebrow(label)
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, fontSize = 20.sp, maxLines = 1)
  }
}

/** The design's dark card: #111, white/10 edge, 24dp corners. Pressing warms the edge to the accent. */
@Composable
fun SurfaceCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val cardColors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow, contentColor = colors.onSurface)
  if (onClick == null) {
    Card(modifier.fillMaxWidth(), colors = cardColors, border = BorderStroke(1.dp, colors.cardBorder)) { content() }
  } else {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val edge by animateColorAsState(if (pressed) colors.accent.copy(alpha = 0.5f) else colors.cardBorder, tween(300), label = "edge")
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = cardColors, border = BorderStroke(1.dp, edge), interactionSource = interaction) { content() }
  }
}

@Composable
fun ConfirmDialog(
  title: String,
  text: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  destructive: Boolean = false,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = {
      TextButton(
        onClick = {
          onDismiss()
          onConfirm()
        },
        colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors(),
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun TextPromptDialog(title: String, initial: String, label: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
  var text by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = true) },
    confirmButton = {
      TextButton(
        enabled = text.isNotBlank(),
        onClick = {
          onDismiss()
          onConfirm(text.trim())
        },
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

/**
 * Compact numeric cell for set tables. Keeps its own text so partial input ("22.") survives the
 * round trip through the stored value; resyncs only when the value changes from outside.
 */
@Composable
fun NumberCell(
  value: Double?,
  onValueChange: (Double?) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  decimal: Boolean = true,
  done: Boolean = false,
) {
  var text by remember { mutableStateOf(value?.let(Units::number) ?: "") }
  LaunchedEffect(value) {
    val parsed = Units.parseDecimal(text)
    val same = (parsed == null && value == null) || (parsed != null && value != null && abs(parsed - value) < 1e-6)
    if (!same) text = value?.let(Units::number) ?: ""
  }
  CellField(
    text = text,
    onTextChange = { t ->
      val clean = t.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(7)
      text = clean
      onValueChange(Units.parseDecimal(clean))
    },
    placeholder = placeholder,
    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
    modifier = modifier,
    done = done,
  )
}

/** Duration cell: accepts "90" or "1:30", shows m:ss once the value comes back from outside. */
@Composable
fun DurationCell(value: Int?, onValueChange: (Int?) -> Unit, placeholder: String, modifier: Modifier = Modifier, done: Boolean = false) {
  var text by remember { mutableStateOf(value?.let { Units.clock(it.toLong()) } ?: "") }
  LaunchedEffect(value) { if (Units.parseClock(text) != value) text = value?.let { Units.clock(it.toLong()) } ?: "" }
  CellField(
    text = text,
    onTextChange = { t ->
      val clean = t.filter { it.isDigit() || it == ':' }.take(8)
      text = clean
      onValueChange(Units.parseClock(clean))
    },
    placeholder = placeholder,
    keyboardType = KeyboardType.Number,
    modifier = modifier,
    done = done,
  )
}

@Composable
private fun CellField(text: String, onTextChange: (String) -> Unit, placeholder: String, keyboardType: KeyboardType, modifier: Modifier, done: Boolean) {
  val colors = MaterialTheme.colorScheme
  val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
  BasicTextField(
    value = text,
    onValueChange = onTextChange,
    singleLine = true,
    textStyle = textStyle,
    cursorBrush = SolidColor(colors.primary),
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
    modifier =
      modifier
        .background(if (done) Color.Transparent else colors.surfaceContainerHighest, RoundedCornerShape(10.dp))
        .padding(horizontal = 4.dp, vertical = 8.dp),
    decorationBox = { inner ->
      Box(contentAlignment = Alignment.Center) {
        if (text.isEmpty()) Text(placeholder, style = textStyle.copy(color = colors.onSurfaceVariant.copy(alpha = 0.55f)), maxLines = 1)
        inner()
      }
    },
  )
}

/** Scaffold padding plus comfortable page margins for lists. */
fun pagePadding(scaffold: PaddingValues, horizontal: androidx.compose.ui.unit.Dp = 16.dp, vertical: androidx.compose.ui.unit.Dp = 12.dp) =
  PaddingValues(start = horizontal, end = horizontal, top = scaffold.calculateTopPadding() + vertical, bottom = scaffold.calculateBottomPadding() + vertical + 24.dp)
