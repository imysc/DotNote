package com.cookandroid.dotnote.presentation.memo

import android.content.Intent
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info // Placeholder for Mic
import androidx.compose.material.icons.filled.Star // Placeholder for Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoInputScreen(
    viewModel: MemoViewModel,
    onNavigateBack: () -> Unit
) {
    var content by remember { mutableStateOf("") }
    val isSaving by viewModel.isSaving.collectAsState()
    val context = LocalContext.current

    // STT Launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0) ?: ""
            if (spokenText.isNotEmpty()) {
                content = if (content.isEmpty()) spokenText else "$content\n$spokenText"
            }
        }
    }

    // OCR Launcher (Take Picture Preview)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val recognizedText = visionText.text
                    if (recognizedText.isNotEmpty()) {
                        content = if (content.isEmpty()) recognizedText else "$content\n$recognizedText"
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("새 메모") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveMemo(content)
                            onNavigateBack()
                        },
                        enabled = content.isNotBlank() && !isSaving
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    // STT Button
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                        }
                        speechRecognizerLauncher.launch(intent)
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Mic (STT)") // Using Info icon as placeholder for Mic
                    }
                    
                    // OCR Button
                    IconButton(onClick = {
                        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (hasCamera) {
                            cameraLauncher.launch(null)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Icon(Icons.Default.Star, contentDescription = "Camera (OCR)") // Using Star icon as placeholder for Camera
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AI가 메모를 분석하여 자동 태그를 생성 중입니다...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = { Text("어떤 아이디어가 떠오르셨나요?\n마이크나 카메라 버튼을 활용해보세요.") },
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
