package com.subradar.app.feature

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subradar.app.R
import com.subradar.app.data.Backup
import com.subradar.app.data.BackupCodec
import com.subradar.app.data.PreferenceStore
import com.subradar.app.domain.Preferences
import com.subradar.app.domain.RadarRepository
import com.subradar.app.domain.Snapshot
import com.subradar.app.domain.Subscription
import com.subradar.app.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val app: Application,
    private val repository: RadarRepository,
    private val store: PreferenceStore,
    private val scheduler: ReminderScheduler
) : AndroidViewModel(app) {
    private val mutations = Mutex()
    val data = repository.snapshots.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val preferences = store.values.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())
    val language = store.languages.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val messages = Channel<Int>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    fun save(sub: Subscription, completed: (Boolean) -> Unit) = action(completed = completed) {
        repository.save(sub, LocalDate.now())
        scheduler.checkSoon()
    }

    fun archive(id: String) = action { repository.archive(id); scheduler.checkSoon() }
    fun delete(id: String, completed: (Boolean) -> Unit) = action(completed = completed) {
        repository.delete(id)
        scheduler.checkSoon()
    }
    fun used(id: String) = action { repository.markUsed(id, LocalDate.now()) }
    fun category(name: String, completed: (String) -> Unit) = action {
        completed(repository.addCategory(name).id)
    }
    fun confirm(id: String, date: LocalDate) = action {
        repository.confirm(id, date, LocalDate.now())
        scheduler.checkSoon()
    }
    fun undoConfirmation(id: String) = action { repository.removePayment(id); scheduler.checkSoon() }
    fun changePreferences(transform: (Preferences) -> Preferences) = action {
        store.update(transform)
        if (!store.current().notifications) scheduler.dismissAll()
        scheduler.checkSoon()
    }
    fun setLanguage(tag: String) = action { store.setLanguage(tag); scheduler.checkSoon() }
    fun refreshReminders() = scheduler.checkSoon()

    fun export(uri: Uri, json: Boolean) = action(success = R.string.exported) {
        withContext(Dispatchers.IO) {
            val snapshot = repository.snapshot()
            val text = if (json) BackupCodec.encode(Backup(1, snapshot, store.current(), store.language())).also {
                require(it.encodeToByteArray().size <= BackupCodec.limit)
            } else BackupCodec.csv(snapshot)
            checkNotNull(app.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(text) }
        }
    }

    fun restore(uri: Uri) = action(error = R.string.invalid_backup, success = R.string.imported) {
        val backup = withContext(Dispatchers.IO) {
            val bytes = ByteArrayOutputStream()
            checkNotNull(app.contentResolver.openInputStream(uri)).use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    require(bytes.size() + n <= BackupCodec.limit)
                    bytes.write(buffer, 0, n)
                }
            }
            BackupCodec.decode(bytes.toByteArray().decodeToString(throwOnInvalidSequence = true))
        }
        repository.replace(backup.data)
        store.restore(backup.preferences, backup.language)
        scheduler.dismissAll()
        scheduler.checkSoon()
    }

    fun clear() = action(success = R.string.cleared) {
        repository.clear()
        store.restore(Preferences(), "")
        scheduler.dismissAll()
        scheduler.checkSoon()
    }

    private fun action(
        error: Int = R.string.operation_error,
        success: Int? = null,
        completed: (Boolean) -> Unit = {},
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            val worked = try {
                mutations.withLock { block() }
                if (success != null) messages.send(success)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                messages.send(error)
                false
            }
            completed(worked)
        }
    }
}
