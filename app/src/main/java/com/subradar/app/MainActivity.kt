package com.subradar.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.subradar.app.feature.RadarViewModel
import com.subradar.app.feature.RadarApp
import com.subradar.app.domain.Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: RadarViewModel by viewModels()
    private val openSubscription = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateSystemBars(viewModel.preferences.value.theme)
        lifecycleScope.launch {
            viewModel.preferences.collect { updateSystemBars(it.theme) }
        }
        if (savedInstanceState == null) openSubscription.value = intent.getStringExtra(SUBSCRIPTION_ID)
        lifecycleScope.launch {
            viewModel.language.filterNotNull().collect { tag ->
                val locales = LocaleListCompat.forLanguageTags(tag)
                if (AppCompatDelegate.getApplicationLocales() != locales) {
                    AppCompatDelegate.setApplicationLocales(locales)
                }
            }
        }
        setContent { RadarApp(viewModel, openSubscription) { openSubscription.value = null } }
    }

    private fun updateSystemBars(theme: Theme) {
        val style = if (theme == Theme.DARK) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openSubscription.value = intent.getStringExtra(SUBSCRIPTION_ID)
    }

    companion object { const val SUBSCRIPTION_ID = "subscription_id" }
}
