package com.example.financeplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.financeplanner.data.ai.LlmRepository
import com.example.financeplanner.ui.navigation.AppNavigation
import com.example.financeplanner.ui.theme.FinancePlannerTheme
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Koin стартует в FinancePlannerApp.onCreate() — один раз на процесс.
        setContent {
            FinancePlannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        // ЛР №5, п.9: при завершении работы приложения освобождаем нативные
        // ресурсы llama.cpp (контекст модели, потоки, память) — иначе
        // ~1.2 ГБ остаются за процессом до его убийства системой.
        if (isFinishing) {
            runCatching {
                GlobalContext.getOrNull()?.get<LlmRepository>()?.release()
            }
        }
        super.onDestroy()
    }
}
