package com.example.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import com.example.receiver.ReminderReceiver
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object CalendarReminderHelper {

    fun scheduleAlarm(context: Context, task: Task) {
        val reminderTime = task.reminderTimeMillis ?: return
        if (reminderTime < System.currentTimeMillis()) return // Do not schedule past alarms

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("TASK_ID", task.id)
            putExtra("TASK_TITLE", task.title)
            putExtra("TASK_CATEGORY", task.category)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        reminderTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            // Fallback for security exception
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                reminderTime,
                pendingIntent
            )
        }
    }

    fun cancelAlarm(context: Context, task: Task) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun addTaskToSystemCalendar(context: Context, task: Task): Long? {
        return try {
            val calendarId = getPrimaryCalendarId(context) ?: return null
            val startMillis = parseTimeToMillis(task.date, task.startTime) ?: return null
            val endMillis = parseTimeToMillis(task.date, task.endTime) ?: (startMillis + 3600000)

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, task.title)
                put(CalendarContract.Events.DESCRIPTION, "提醒类别: ${task.category}")
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }

            val uri: Uri? = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull()
        } catch (e: SecurityException) {
            // Calendar permission not granted
            null
        } catch (e: Exception) {
            null
        }
    }

    fun deleteFromSystemCalendar(context: Context, eventId: Long) {
        try {
            val deleteUri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, eventId.toString())
            context.contentResolver.delete(deleteUri, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateSystemCalendarEvent(context: Context, eventId: Long, task: Task) {
        try {
            val updateUri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, eventId.toString())
            val startMillis = parseTimeToMillis(task.date, task.startTime) ?: return
            val endMillis = parseTimeToMillis(task.date, task.endTime) ?: (startMillis + 3600000)

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, endMillis)
                put(CalendarContract.Events.TITLE, task.title)
                put(CalendarContract.Events.DESCRIPTION, "提醒类别: ${task.category}")
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            context.contentResolver.update(updateUri, values, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val isPrimary = cursor.getInt(1)
                    if (isPrimary == 1) return id
                }
            }
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0)
                }
            }
        } catch (e: SecurityException) {
            // No permission
        }
        return 1L
    }

    fun parseTimeToMillis(dateStr: String, timeStr: String): Long? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            format.parse("$dateStr $timeStr")?.time
        } catch (e: Exception) {
            null
        }
    }
}
