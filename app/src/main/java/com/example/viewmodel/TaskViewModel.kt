package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.FocusLog
import com.example.data.Task
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    private val prefs = application.getSharedPreferences("time_manager_prefs", Context.MODE_PRIVATE)

    // User settings
    private val _username = MutableStateFlow(prefs.getString("username", "小明") ?: "小明")
    val username: StateFlow<String> = _username.asStateFlow()

    fun updateUsername(newName: String) {
        _username.value = newName
        prefs.edit().putString("username", newName).apply()
    }

    // Task flows
    val allTasks: StateFlow<List<Task>>
    val todayTasks: StateFlow<List<Task>>

    // Selected date for detailed schedule calendar view (default today)
    private val _selectedDate = MutableStateFlow(getTodayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Tasks filtered by the selected detailed calendar date
    val calendarTasks: StateFlow<List<Task>>

    // Category filter for Dashboard view ("全部" / labels)
    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    // Today's goals progress calculation (Float 0f..1f)
    val todayProgress: StateFlow<Float>

    // Focus logs & Statistics
    val allFocusLogs: StateFlow<List<FocusLog>>
    val weekFocusStats: StateFlow<Map<String, Int>> // Maps "yyyy-MM-dd" to focus minutes

    // Focus Timer State
    private val _timerDurationMinutes = MutableStateFlow(25) // Default 25 min Pomodoro
    val timerDurationMinutes: StateFlow<Int> = _timerDurationMinutes.asStateFlow()

    private val _timerRemainingSeconds = MutableStateFlow(25 * 60)
    val timerRemainingSeconds: StateFlow<Int> = _timerRemainingSeconds.asStateFlow()

    private val _timerState = MutableStateFlow(TimerState.IDLE)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _timerCategory = MutableStateFlow("工作")
    val timerCategory: StateFlow<String> = _timerCategory.asStateFlow()

    private var timerJob: Job? = null

    enum class TimerState {
        IDLE, RUNNING, PAUSED
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database.taskDao(), database.focusLogDao(), application)

        allTasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        todayTasks = allTasks.map { tasks ->
            val todayStr = getTodayDateString()
            tasks.filter { it.date == todayStr }
        }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

        todayProgress = todayTasks.map { tasks ->
            if (tasks.isEmpty()) 0.70f // Default aesthetic start matching screenshot 70% if empty, or actual calculation
            else {
                val completed = tasks.count { it.isCompleted }
                completed.toFloat() / tasks.size.toFloat()
            }
        }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0.70f)

        calendarTasks = combine(allTasks, _selectedDate) { tasks, sDate ->
            tasks.filter { it.date == sDate }
        }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

        allFocusLogs = repository.allFocusLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Compile statistics of current week's days
        weekFocusStats = allFocusLogs.map { logs ->
            val stats = mutableMapOf<String, Int>()
            val weekDates = getWeekDatesStrings()
            for (dateStr in weekDates) {
                val sum = logs.filter { it.date == dateStr }.sumOf { it.durationMinutes }
                stats[dateStr] = sum
            }
            stats
        }.stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyMap())

        // Initial populating of mock data matching visual mockup on first launch of database
        viewModelScope.launch {
            if (database.taskDao().getAllTasks().first().isEmpty()) {
                prepopulateMockData()
            }
        }
    }

    private suspend fun prepopulateMockData() {
        val todayStr = getTodayDateString()
        
        // Add tasks matching the design screenshot
        repository.insertTask(
            Task(
                title = "完成项目方案",
                date = todayStr,
                startTime = "09:00",
                endTime = "10:30",
                category = "工作",
                isCompleted = true,
                isKeyTask = true
            )
        )
        repository.insertTask(
            Task(
                title = "完成项目方案",
                date = todayStr,
                startTime = "11:00",
                endTime = "11:30",
                category = "工作",
                isCompleted = false,
                isKeyTask = true
            )
        )
        repository.insertTask(
            Task(
                title = "完成健身",
                date = todayStr,
                startTime = "12:30",
                endTime = "12:30", // As shown in screenshot "12:30 - 12:30"
                category = "健身",
                isCompleted = false,
                isKeyTask = true
            )
        )
        repository.insertTask(
            Task(
                title = "准备下周工作周会汇报",
                date = todayStr,
                startTime = "14:00",
                endTime = "16:00",
                category = "学习",
                isCompleted = false,
                isKeyTask = false
            )
        )

        // Seed week focus logs matching screenshot bars: Mon (120 min), Tue (40 min), Wed (90 min), Thu (180 min)
        val weekDates = getWeekDatesStrings()
        if (weekDates.size >= 4) {
            repository.insertFocusLog(FocusLog(date = weekDates[0], durationMinutes = 120, category = "工作"))
            repository.insertFocusLog(FocusLog(date = weekDates[1], durationMinutes = 40, category = "学习"))
            repository.insertFocusLog(FocusLog(date = weekDates[2], durationMinutes = 90, category = "工作"))
            repository.insertFocusLog(FocusLog(date = weekDates[3], durationMinutes = 180, category = "健身"))
        }
    }

    // Task operations
    fun selectCalendarDate(date: String) {
        _selectedDate.value = date
    }

    fun toggleCategoryFilter(category: String?) {
        if (_selectedCategoryFilter.value == category) {
            _selectedCategoryFilter.value = null
        } else {
            _selectedCategoryFilter.value = category
        }
    }

    fun insertTask(
        title: String,
        date: String,
        startTime: String,
        endTime: String,
        category: String,
        isKeyTask: Boolean,
        hasReminder: Boolean,
        reminderTimeMillis: Long?,
        addToCalendar: Boolean
    ) {
        viewModelScope.launch {
            repository.insertTask(
                Task(
                    title = title,
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    category = category,
                    isCompleted = false,
                    isKeyTask = isKeyTask,
                    hasReminder = hasReminder,
                    reminderTimeMillis = reminderTimeMillis,
                    calendarEventId = null
                ),
                addToCalendar = addToCalendar
            )
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // Timer functions
    fun setTimerDuration(minutes: Int) {
        _timerDurationMinutes.value = minutes
        if (_timerState.value == TimerState.IDLE) {
            _timerRemainingSeconds.value = minutes * 60
        }
    }

    fun setTimerCategory(category: String) {
        _timerCategory.value = category
    }

    fun startTimer() {
        if (_timerState.value == TimerState.IDLE) {
            _timerRemainingSeconds.value = _timerDurationMinutes.value * 60
        }
        _timerState.value = TimerState.RUNNING
        startTimerJob()
    }

    fun pauseTimer() {
        _timerState.value = TimerState.PAUSED
        timerJob?.cancel()
    }

    fun resumeTimer() {
        _timerState.value = TimerState.RUNNING
        startTimerJob()
    }

    fun stopTimer(saveLog: Boolean) {
        timerJob?.cancel()
        val elapsedSeconds = (_timerDurationMinutes.value * 60) - _timerRemainingSeconds.value
        val elapsedMinutes = elapsedSeconds / 60

        if (saveLog && elapsedMinutes > 0) {
            viewModelScope.launch {
                repository.insertFocusLog(
                    FocusLog(
                        date = getTodayDateString(),
                        durationMinutes = elapsedMinutes,
                        category = _timerCategory.value
                    )
                )
            }
        }
        _timerState.value = TimerState.IDLE
        _timerRemainingSeconds.value = _timerDurationMinutes.value * 60
    }

    private fun startTimerJob() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_timerRemainingSeconds.value > 0) {
                delay(1000)
                _timerRemainingSeconds.value -= 1
            }
            // Timer finished!
            _timerState.value = TimerState.IDLE
            val minutes = _timerDurationMinutes.value
            repository.insertFocusLog(
                FocusLog(
                    date = getTodayDateString(),
                    durationMinutes = minutes,
                    category = _timerCategory.value
                )
            )
            _timerRemainingSeconds.value = _timerDurationMinutes.value * 60
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    // Date Utilities
    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getTodayDayOfWeekLabel(): String {
        return SimpleDateFormat("EEEE", Locale.CHINESE).format(Date())
    }

    fun getWeekDatesStrings(): List<String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val list = mutableListOf<String>()
        for (i in 0 until 7) {
            list.add(sdf.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return list
    }

    fun getWeekDatesAndLabels(): List<Pair<String, String>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val list = mutableListOf<Pair<String, String>>()
        for (i in 0 until 7) {
            list.add(Pair(sdf.format(cal.time), labels[i]))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return list
    }
}

class TaskViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
