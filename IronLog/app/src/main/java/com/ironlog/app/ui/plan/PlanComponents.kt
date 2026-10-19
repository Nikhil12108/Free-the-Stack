package com.ironlog.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironlog.app.data.PlanOverview
import com.ironlog.app.data.db.PlanSessionEntity
import com.ironlog.app.data.db.SessionStatus
import com.ironlog.app.progression.Periodization
import com.ironlog.app.progression.Priority
import com.ironlog.app.theme.EyebrowStyle
import com.ironlog.app.ui.components.SelectChip

/** "W1 · 3" … "DL": one pill per week, the selected one filled, finished weeks dimmed. */
@Composable
fun WeekStrip(weeks: Int, schedule: List<Int>, current: Int, selected: Int, done: (Int) -> Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (w in 0 until weeks) {
      val isSelected = w == selected
      val deload = Periodization.isDeload(w, weeks)
      val shape = RoundedCornerShape(14.dp)
      Column(
        Modifier.widthIn(min = 52.dp)
          .clip(shape)
          .background(if (isSelected) colors.primary else Color.Transparent, shape)
          .border(1.dp, if (w == current && !isSelected) colors.onSurface else colors.outline, shape)
          .clickable { onSelect(w) }
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        val fg = if (isSelected) colors.onPrimary else if (done(w)) colors.onSurfaceVariant else colors.onSurface
        Text(if (deload) "DL" else "W${w + 1}", style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.SemiBold)
        Text(if (deload) "easy" else "${schedule.getOrNull(w) ?: "–"} RIR", style = EyebrowStyle, color = fg.copy(alpha = 0.75f))
      }
    }
  }
}

fun weekLabel(ov: PlanOverview, week: Int): String =
  if (ov.isDeload(week)) "Deload week · keep every set easy" else "Week ${week + 1} of ${ov.plan.weeks - 1} · stop ${ov.rir(week)} rep${if (ov.rir(week) == 1) "" else "s"} short of failure"

fun statusLabel(s: PlanSessionEntity, next: PlanSessionEntity?): String =
  when {
    s.id == next?.id -> "Next"
    s.status == SessionStatus.DONE -> "Done"
    s.status == SessionStatus.PARTIAL -> "Partly done"
    s.status == SessionStatus.SKIPPED -> "Skipped"
    else -> "Upcoming"
  }

@Composable
fun PriorityChips(selected: Priority, onSelect: (Priority) -> Unit, modifier: Modifier = Modifier) {
  FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Priority.entries.forEach { p -> SelectChip(selected = p == selected, onClick = { onSelect(p) }, label = p.label) }
  }
}
