package com.cookandroid.dotnote.presentation.memo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cookandroid.dotnote.data.local.entity.MemoEntity
import com.cookandroid.dotnote.domain.repository.SlmRepository
import com.cookandroid.dotnote.domain.repository.MemoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MemoViewModel(
    private val memoRepository: MemoRepository,
    private val slmRepository: SlmRepository
) : ViewModel() {

    private val _memosWithTags = MutableStateFlow<List<com.cookandroid.dotnote.data.local.entity.MemoWithTags>>(emptyList())
    val memosWithTags: StateFlow<List<com.cookandroid.dotnote.data.local.entity.MemoWithTags>> = _memosWithTags.asStateFlow()

    private val _relations = MutableStateFlow<List<com.cookandroid.dotnote.data.local.entity.MemoRelationEntity>>(emptyList())
    val relations: StateFlow<List<com.cookandroid.dotnote.data.local.entity.MemoRelationEntity>> = _relations.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    init {
        loadMemos()
        loadRelations()
    }

    private fun loadMemos() {
        viewModelScope.launch {
            memoRepository.getAllMemosWithTags().collect { memoList ->
                _memosWithTags.value = memoList
            }
        }
    }

    private fun loadRelations() {
        viewModelScope.launch {
            memoRepository.getAllMemoRelations().collect { relationList ->
                _relations.value = relationList
            }
        }
    }

    fun saveMemo(content: String, latitude: Double? = null, longitude: Double? = null) {
        if (content.isBlank()) return
        
        viewModelScope.launch {
            _isSaving.value = true
            
            // 1. 로컬 SLM 분석 수행 (태그 & 시맨틱 관계망 추출)
            val result = slmRepository.analyzeMemo(content)
            
            // 2. Room DB에 메모, 태그, 관계 일괄 저장
            memoRepository.insertMemo(content, result.tags, result.relations, latitude, longitude)
            
            _isSaving.value = false
        }
    }

    fun updateMemo(memoId: Long, newContent: String, newTags: List<String>, latitude: Double? = null, longitude: Double? = null) {
        if (newContent.isBlank()) return
        viewModelScope.launch {
            memoRepository.updateMemo(memoId, newContent, newTags, emptyList(), latitude, longitude)
        }
    }

    fun deleteMemo(memoId: Long) {
        viewModelScope.launch {
            memoRepository.deleteMemo(memoId)
        }
    }
}
