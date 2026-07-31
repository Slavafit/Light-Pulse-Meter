package com.slavafit.lightflicker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slavafit.lightflicker.data.AppSettings
import com.slavafit.lightflicker.data.SettingsRepository
import com.slavafit.lightflicker.data.ThemeMode
import com.slavafit.lightflicker.measurement.FrameSample
import com.slavafit.lightflicker.measurement.MeasurementResult
import com.slavafit.lightflicker.measurement.SignalProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    val settings: StateFlow<AppSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestSample = MutableStateFlow<FrameSample?>(null)
    val progress = MutableStateFlow(0f)
    val result = MutableStateFlow<MeasurementResult?>(null)
    val measuring = MutableStateFlow(false)
    private val samples = mutableListOf<FrameSample>()
    private var startedNs = 0L

    fun onFrame(sample: FrameSample) {
        latestSample.value = sample
        if (!measuring.value) return
        if (startedNs == 0L) startedNs = sample.timestampNs
        samples += sample
        progress.value = ((sample.timestampNs - startedNs) / 4_000_000_000f).coerceIn(0f, 1f)
        if (sample.timestampNs - startedNs >= 4_000_000_000L) finishMeasurement()
    }

    fun startMeasurement() {
        samples.clear()
        startedNs = 0L
        progress.value = 0f
        result.value = null
        measuring.value = true
    }

    private fun finishMeasurement() {
        if (!measuring.value) return
        measuring.value = false
        val copy = samples.toList()
        viewModelScope.launch {
            result.value = withContext(Dispatchers.Default) { SignalProcessor.process(copy) }
        }
    }

    fun resetMeasurement() {
        measuring.value = false
        result.value = null
        progress.value = 0f
        latestSample.value = null
        samples.clear()
    }

    fun completeOnboarding() = viewModelScope.launch { repository.completeOnboarding() }
    fun setLanguage(value: String) = viewModelScope.launch { repository.setLanguage(value) }
    fun setTheme(value: ThemeMode) = viewModelScope.launch { repository.setTheme(value) }
}
