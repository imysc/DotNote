package com.cookandroid.dotnote.presentation.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cookandroid.dotnote.presentation.memo.DetailCardScreen
import com.cookandroid.dotnote.presentation.memo.MemoViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MemoViewModel,
    onNavigateBack: () -> Unit
) {
    val memosWithTags by viewModel.memosWithTags.collectAsState()
    val memos = memosWithTags.map { it.memo }
    val relations by viewModel.relations.collectAsState()

    var selectedMemoId by remember { mutableStateOf<Long?>(null) }
    var showDetailBottomSheet by remember { mutableStateOf(false) }
    
    // Default to Seoul if no memos have location
    val defaultLocation = LatLng(37.5665, 126.9780)
    
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 10f)
    }

    // 날짜 포맷
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd  HH:mm", Locale.getDefault()) }

    // 메모 목록이 로드되거나 변경되면, 위치가 존재하는 핀들의 평균 위치 좌표로 지도 카메라 자동 줌 핏 이동
    LaunchedEffect(memos) {
        val validMemos = memos.filter { it.latitude != null && it.longitude != null }
        if (validMemos.isNotEmpty()) {
            val avgLat = validMemos.map { it.latitude!! }.average()
            val avgLng = validMemos.map { it.longitude!! }.average()
            cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(avgLat, avgLng), 13f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("생각의 지도") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapClick = {
                    selectedMemoId = null // 빈 지도 탭 시 선택 해제
                }
            ) {
                // 1. 모든 메모 마커 표시
                memos.forEach { memo ->
                    if (memo.latitude != null && memo.longitude != null) {
                        val position = LatLng(memo.latitude, memo.longitude)
                        Marker(
                            state = MarkerState(position = position),
                            title = "작성일시: ${dateFormat.format(Date(memo.createdAt))}",
                            snippet = memo.content.take(25).trim() + if (memo.content.length > 25) "..." else "",
                            onClick = {
                                selectedMemoId = memo.id
                                false // false를 반환하면 구글 맵 기본 InfoWindow가 노출되며 카메라가 핀 위치로 정렬됩니다.
                            }
                        )
                    }
                }

                // 2. 선택된 메모의 지리적 시맨틱 엣지 연결 (Polyline)
                selectedMemoId?.let { id ->
                    val selectedMemo = memos.find { it.id == id }
                    if (selectedMemo != null && selectedMemo.latitude != null && selectedMemo.longitude != null) {
                        val selectedLatLng = LatLng(selectedMemo.latitude, selectedMemo.longitude)
                        
                        // 현재 메모와 연결된 관계 추출
                        val connectedRelations = relations.filter { it.parentId == id || it.childId == id }
                        
                        connectedRelations.forEach { relation ->
                            val otherId = if (relation.parentId == id) relation.childId else relation.parentId
                            val otherMemo = memos.find { it.id == otherId }
                            if (otherMemo != null && otherMemo.latitude != null && otherMemo.longitude != null) {
                                val otherLatLng = LatLng(otherMemo.latitude, otherMemo.longitude)
                                Polyline(
                                    points = listOf(selectedLatLng, otherLatLng),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    width = 8f,
                                    geodesic = true
                                )
                            }
                        }
                    }
                }
            }

            // 3. 선택된 메모 요약 바텀 카드 노출
            selectedMemoId?.let { id ->
                val selectedMemoWithTags = memosWithTags.find { it.memo.id == id }
                if (selectedMemoWithTags != null) {
                    val memo = selectedMemoWithTags.memo
                    
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "선택된 메모",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(
                                    onClick = { selectedMemoId = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = memo.content,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateFormat.format(Date(memo.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Button(
                                    onClick = {
                                        showDetailBottomSheet = true
                                    },
                                    shape = RoundedCornerShape(50),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("상세 보기")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Detail Bottom Sheet 연동
    if (showDetailBottomSheet) {
        selectedMemoId?.let { id ->
            val selectedMemoWithTags = memosWithTags.find { it.memo.id == id }
            if (selectedMemoWithTags != null) {
                val selectedMemo = selectedMemoWithTags.memo
                val tags = selectedMemoWithTags.tags.map { it.name }
                val relatedMemosList = relations.filter { it.parentId == id || it.childId == id }
                    .mapNotNull { relation ->
                        val otherId = if (relation.parentId == id) relation.childId else relation.parentId
                        val otherMemo = memos.find { it.id == otherId }
                        if (otherMemo != null) {
                            Pair(otherMemo, relation.relationType ?: "관련됨")
                        } else null
                    }

                DetailCardScreen(
                    memoId = selectedMemo.id,
                    content = selectedMemo.content,
                    createdAt = selectedMemo.createdAt,
                    onClose = { 
                        showDetailBottomSheet = false 
                    },
                    onUpdate = { memoId, newContent, newTags ->
                        viewModel.updateMemo(
                            memoId = memoId,
                            newContent = newContent,
                            newTags = newTags,
                            latitude = selectedMemo.latitude,
                            longitude = selectedMemo.longitude
                        )
                    },
                    onDelete = { memoId ->
                        viewModel.deleteMemo(memoId)
                        showDetailBottomSheet = false
                        selectedMemoId = null
                    },
                    tags = tags,
                    relatedMemos = relatedMemosList
                )
            }
        }
    }
}
