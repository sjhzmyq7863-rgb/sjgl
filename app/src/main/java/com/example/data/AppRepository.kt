package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val taskDao: TaskDao,
    private val focusLogDao: FocusLogDao,
    private val context: Context
) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val allFocusLogs: Flow<List<FocusLog>> = focusLogDao.getAllFocusLogs()

    fun getTasksForDate(date: String): Flow<List<Task>> = taskDao.getTasksForDate(date)

    suspend fun insertTask(task: Task, addToCalendar: Boolean = false): Long {
        val id = taskDao.insertTask(task)
        var updatedTask = task.copy(id = id.toInt())

        if (updatedTask.hasReminder) {
            CalendarReminderHelper.scheduleAlarm(context, updatedTask)
        }

        if (addToCalendar) {
            val calendarId = CalendarReminderHelper.addTaskToSystemCalendar(context, updatedTask)
            if (calendarId != null) {
                updatedTask = updatedTask.copy(calendarEventId = calendarId)
                taskDao.updateTask(updatedTask)
            }
        }

        return id
    }

    suspend fun updateTask(task: Task, addToCalendar: Boolean = false) {
        taskDao.updateTask(task)

        CalendarReminderHelper.cancelAlarm(context, task)
        if (task.hasReminder) {
            CalendarReminderHelper.scheduleAlarm(context, task)
        }

        if (task.calendarEventId != null) {
            CalendarReminderHelper.updateSystemCalendarEvent(context, task.calendarEventId, task)
        } else if (addToCalendar) {
            val calendarId = CalendarReminderHelper.addTaskToSystemCalendar(context, task)
            if (calendarId != null) {
                taskDao.updateTask(task.copy(calendarEventId = calendarId))
            }
        }
    }

    suspend fun deleteTask(task: Task) {
        taskDao.deleteTask(task)
        CalendarReminderHelper.cancelAlarm(context, task)
        if (task.calendarEventId != null) {
            CalendarReminderHelper.deleteFromSystemCalendar(context, task.calendarEventId)
        }
    }

    suspend fun deleteTaskById(id: Int) {
        val task = taskDao.getTaskById(id)
        if (task != null) {
            deleteTask(task)
        }
    }

    suspend fun insertFocusLog(log: FocusLog) = focusLogDao.insertFocusLog(log)

    suspend fun deleteFocusLog(log: FocusLog) = focusLogDao.deleteFocusLog(log)

    suspend fun clearAllFocusLogs() = focusLogDao.clearAllLogs()
}
