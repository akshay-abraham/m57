// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Inline calendar and time fields, rather than an Android date-picker dialog. */
class CustomPausePicker(context: Context) : LinearLayout(context) {
    private val zone = ZoneId.systemDefault()
    private val initial = ZonedDateTime.now(zone).plusHours(1).withSecond(0).withNano(0)
    private var selected = initial.toLocalDate()
    private var month = YearMonth.from(selected)
    private val calendar = LyraUi.column(context, 0)
    private val hour = timeField(initial.hour, "Hour, 0 to 23")
    private val minute = timeField(initial.minute, "Minute, 0 to 59")
    private val summary = LyraUi.text(context, "", 15f, LyraUi.ink, true)

    init {
        orientation = VERTICAL
        addView(calendar)
        addView(LyraUi.text(context, "Choose a time", 18f, bold = true))
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(hour, LayoutParams(0, -2, 1f))
            addView(LyraUi.text(context, ":", 24f, bold = true).apply {
                gravity = Gravity.CENTER; layoutParams = LayoutParams(LyraUi.dp(context, 28), -2)
            })
            addView(minute, LayoutParams(0, -2, 1f))
        })
        addView(LyraUi.text(context, "24-hour time · ${zone.id}", 13f, LyraUi.muted))
        addView(summary)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateSummary() }
        }
        hour.addTextChangedListener(watcher); minute.addTextChangedListener(watcher)
        renderCalendar(); updateSummary()
    }

    private fun timeField(value: Int, label: String) = LyraUi.field(EditText(context)).apply {
        setText(String.format(java.util.Locale.ROOT, "%02d", value))
        contentDescription = label
        gravity = Gravity.CENTER; textSize = 28f
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(android.text.InputFilter.LengthFilter(2))
        setSingleLine(true)
    }

    fun selectedEpoch(): Long? {
        val h = hour.text.toString().toIntOrNull() ?: return null
        val m = minute.text.toString().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        val local = LocalDateTime.of(selected, LocalTime.of(h, m))
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.isEmpty()) return null
        return ZonedDateTime.ofLocal(local, zone, offsets.first()).toInstant().toEpochMilli()
    }

    private fun updateSummary() {
        val epoch = selectedEpoch()
        summary.text = if (epoch == null) "Choose a valid local time."
            else "Resume ${DateTimeFormatter.ofPattern("EEE d MMM · HH:mm z").format(java.time.Instant.ofEpochMilli(epoch).atZone(zone))}"
    }

    private fun renderCalendar() {
        calendar.removeAllViews()
        calendar.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(LyraUi.button(context, "‹", false) {
                month = month.minusMonths(1); renderCalendar()
            }.apply {
                contentDescription = "Previous month"
                isEnabled = month > YearMonth.from(LocalDate.now(zone))
                layoutParams = LayoutParams(LyraUi.dp(context, 48), LyraUi.dp(context, 48))
                setPadding(0, 0, 0, 0)
            })
            addView(LyraUi.text(context, month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), 18f, bold = true).apply {
                gravity = Gravity.CENTER; layoutParams = LayoutParams(0, -2, 1f)
            })
            addView(LyraUi.button(context, "›", false) {
                month = month.plusMonths(1); renderCalendar()
            }.apply {
                contentDescription = "Next month"
                layoutParams = LayoutParams(LyraUi.dp(context, 48), LyraUi.dp(context, 48))
                setPadding(0, 0, 0, 0)
            })
        })
        calendar.addView(LinearLayout(context).apply {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                addView(LyraUi.text(context, day, 12f, LyraUi.muted, true).apply {
                    gravity = Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    layoutParams = LayoutParams(0, LyraUi.dp(context, 36), 1f)
                })
            }
        })
        val leading = month.atDay(1).dayOfWeek.value - 1
        val rows = (leading + month.lengthOfMonth() + 6) / 7
        repeat(rows) { row ->
            calendar.addView(LinearLayout(context).apply {
                repeat(7) { column ->
                    val day = row * 7 + column - leading + 1
                    if (day !in 1..month.lengthOfMonth()) {
                        addView(View(context), LayoutParams(0, LyraUi.dp(context, 48), 1f))
                    } else {
                        val date = month.atDay(day)
                        addView(LyraUi.button(context, day.toString(), date == selected) {
                            selected = date; renderCalendar(); updateSummary()
                        }.apply {
                            textSize = 14f
                            isEnabled = !date.isBefore(LocalDate.now(zone))
                            alpha = if (isEnabled) 1f else 0.3f
                            isSelected = date == selected
                            contentDescription = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")) + if (isSelected) ", selected" else ""
                            if (date != selected) {
                                background = LyraUi.shape(Color.TRANSPARENT, LyraUi.dp(context, 16).toFloat())
                            }
                            minWidth = 0; minimumWidth = 0
                            minHeight = LyraUi.dp(context, 48); minimumHeight = LyraUi.dp(context, 48)
                            setPadding(0, 0, 0, 0)
                            layoutParams = LayoutParams(0, LyraUi.dp(context, 48), 1f)
                        })
                    }
                }
            })
        }
    }
}
