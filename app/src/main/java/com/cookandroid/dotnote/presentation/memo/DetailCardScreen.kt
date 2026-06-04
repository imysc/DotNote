package com.cookandroid.dotnote.presentation.memo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailCardScreen(
    memoId: Long,
    content: String,
    createdAt: Long,
    onClose: () -> Unit,
    onUpdate: (Long, String, List<String>) -> Unit,
    onDelete: (Long) -> Unit,
    tags: List<String> = emptyList(),
    relatedMemos: List<Pair<com.cookandroid.dotnote.data.local.entity.MemoEntity, String>> = emptyList()
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedContent by remember(content) { mutableStateOf(content) }
    var editedTags by remember(tags) { mutableStateOf(tags.joinToString(", ")) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.getDefault()) }
    val formattedDate = remember(createdAt) { dateFormat.format(Date(createdAt)) }

    // 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("메모 삭제") },
            text = { Text("이 메모를 삭제하시겠습니까?\n삭제된 메모는 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(memoId)
                        showDeleteDialog = false
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            // 편집 중이면 저장 후 닫기
            if (isEditing && (editedContent != content || editedTags != tags.joinToString(", "))) {
                val updatedTags = editedTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                onUpdate(memoId, editedContent, updatedTags)
            }
            onClose()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: 제목 + 작성 일시
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "메모 상세",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // Tags
            if (tags.isNotEmpty()) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Content Area
            if (isEditing) {
                OutlinedTextField(
                    value = editedTags,
                    onValueChange = { editedTags = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    label = { Text("태그 (쉼표로 구분)") }
                )
                
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    label = { Text("메모 내용 수정") }
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isEditing) {
                    // 편집 모드: 취소 + 저장
                    OutlinedButton(
                        onClick = {
                            editedContent = content // 원본으로 복구
                            editedTags = tags.joinToString(", ")
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("취소")
                    }
                    Button(
                        onClick = {
                            val updatedTags = editedTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            onUpdate(memoId, editedContent, updatedTags)
                            isEditing = false
                        },
                        modifier = Modifier.weight(1f),
                        enabled = editedContent.isNotBlank()
                    ) {
                        Text("저장")
                    }
                } else {
                    // 읽기 모드: 수정 + 삭제
                    OutlinedButton(
                        onClick = { isEditing = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("수정")
                    }
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("삭제")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI 추천 (연관 메모) 섹션
            Text(
                text = "연관 메모 (AI 추천)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            if (relatedMemos.isEmpty()) {
                Text(
                    text = "아직 충분한 데이터가 없거나 연관된 메모가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    relatedMemos.forEach { (memo, relationReason) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 연관 이유 배지 (예: 태그 이름)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = relationReason,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = memo.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
