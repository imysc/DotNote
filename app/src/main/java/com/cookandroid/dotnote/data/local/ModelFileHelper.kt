package com.cookandroid.dotnote.data.local

import android.content.Context
import java.io.File

/**
 * 온디바이스 SLM 모델 파일(.task)의 저장 상태 및 경로를 제공하는 헬퍼 클래스
 */
object ModelFileHelper {
    private const val MODEL_FILENAME = "gemma-2-2b-it.task"

    /**
     * 기기 내부 저장소(filesDir) 내에 모델 파일이 존재하는지 검사하고 절대 경로를 반환합니다.
     * 파일이 없을 경우 null을 반환합니다.
     */
    fun getModelFilePath(context: Context): String? {
        val modelFile = File(context.filesDir, MODEL_FILENAME)
        return if (modelFile.exists()) {
            modelFile.absolutePath
        } else {
            null
        }
    }
}
