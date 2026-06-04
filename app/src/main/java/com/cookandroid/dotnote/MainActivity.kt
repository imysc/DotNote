package com.cookandroid.dotnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.cookandroid.dotnote.data.local.AppDatabase
import com.cookandroid.dotnote.data.repository.GeminiRepositoryImpl
import com.cookandroid.dotnote.data.repository.MemoRepositoryImpl
import com.cookandroid.dotnote.presentation.memo.MainScreen
import com.cookandroid.dotnote.presentation.memo.MemoInputScreen
import com.cookandroid.dotnote.presentation.memo.MemoViewModel
import com.cookandroid.dotnote.ui.theme.DotNoteTheme
import com.cookandroid.dotnote.presentation.map.MapScreen
import com.cookandroid.dotnote.data.local.ModelFileHelper
import com.cookandroid.dotnote.data.repository.SlmRepositoryImpl
import com.cookandroid.dotnote.domain.repository.SlmRepository
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

class MainActivity : ComponentActivity() {

    // Simple manual DI for Phase 1
    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "dotnote-db"
        ).build()
    }

    private val memoRepository by lazy { 
        MemoRepositoryImpl(database.memoDao())
    }

    private val geminiRepository by lazy {
        GeminiRepositoryImpl(apiKey = BuildConfig.GEMINI_API_KEY)
    }

    private val slmRepository by lazy {
        SlmRepositoryImpl(null, geminiRepository)
    }

    private val viewModelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MemoViewModel::class.java)) {
                return MemoViewModel(memoRepository, slmRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private val viewModel: MemoViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1.5GB 대용량 모델 파일 로딩 시 UI 스레드 블로킹 방지를 위한 백그라운드 비동기 로더 탑재
        lifecycleScope.launch(Dispatchers.IO) {
            val modelPath = ModelFileHelper.getModelFilePath(applicationContext)
            if (modelPath != null) {
                try {
                    Log.d("MainActivity", "온디바이스 SLM 모델 파일 비동기 로드 시작...")
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTokens(512)
                        .build()
                    val loadedEngine = LlmInference.createFromOptions(applicationContext, options)
                    slmRepository.llmInference = loadedEngine
                    Log.d("MainActivity", "온디바이스 SLM 모델 비동기 로드 성공!")
                } catch (e: Throwable) {
                    Log.e("MainActivity", "온디바이스 SLM 모델 비동기 로드 실패: ${e.message}")
                    e.printStackTrace()
                }
            } else {
                Log.d("MainActivity", "온디바이스 모델 파일이 로컬 스토리지에 존재하지 않습니다.")
            }
        }

        setContent {
            DotNoteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(
                                viewModel = viewModel,
                                onNavigateToInput = {}, // Not used anymore
                                onNavigateToMap = {
                                    navController.navigate("map")
                                }
                            )
                        }
                        composable("map") {
                            MapScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}