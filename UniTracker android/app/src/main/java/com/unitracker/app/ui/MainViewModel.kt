package com.unitracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.unitracker.app.data.QuickStats
import com.unitracker.app.data.TaskRepository
import com.unitracker.app.data.local.TaskWithReminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncUiState(
    val isSyncing: Boolean = false,
    val message: String? = null
)

class MainViewModel(private val repository: TaskRepository) : ViewModel() {

    /** Escaneo detallado. */
    val tasks: StateFlow<List<TaskWithReminders>> = repository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Escaneo rápido. */
    val quickStats: StateFlow<QuickStats> = repository.observeQuickStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickStats())

    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    fun sync() {
        if (_syncState.value.isSyncing) return
        viewModelScope.launch {
            _syncState.update { SyncUiState(isSyncing = true) }
            val result = runCatching { repository.sync() }
            _syncState.update {
                result.fold(
                    onSuccess = { s ->
                        SyncUiState(message = "Listo: ${s.total} tareas (${s.added} nuevas)")
                    },
                    onFailure = { e ->
                        SyncUiState(message = "Error: ${e.message ?: e::class.java.simpleName}")
                    }
                )
            }
        }
    }

    // ---------- Alarmas y tareas ----------

    fun addReminder(taskUid: String, minutesBefore: Int) {
        viewModelScope.launch { repository.addReminder(taskUid, minutesBefore) }
    }

    fun setReminderEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setReminderEnabled(id, enabled) }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { repository.deleteReminder(id) }
    }

    fun setCompleted(taskUid: String, completed: Boolean) {
        viewModelScope.launch { repository.setCompleted(taskUid, completed) }
    }

    fun refreshAlarms() {
        viewModelScope.launch { repository.refreshAlarms() }
    }

    fun canScheduleExactAlarms(): Boolean = repository.canScheduleExactAlarms()

    companion object {
        fun factory(repository: TaskRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(repository) as T
        }
    }
}
