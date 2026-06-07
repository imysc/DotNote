package com.cookandroid.dotnote.presentation.memo

import android.content.Intent
import android.graphics.Bitmap
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Build
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import com.cookandroid.dotnote.presentation.canvas.Edge
import com.cookandroid.dotnote.presentation.canvas.GraphCanvas
import com.cookandroid.dotnote.presentation.canvas.Node
import com.cookandroid.dotnote.presentation.canvas.ViewMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import androidx.compose.material.icons.filled.LocationOn

private fun normalizeText(text: String): String {
    return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC).trim()
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MemoViewModel,
    onNavigateToInput: () -> Unit, // Keeping this for backward compatibility in NavHost if needed, but we won't use it.
    onNavigateToMap: () -> Unit
) {
    val memosWithTags by viewModel.memosWithTags.collectAsState()
    val memos = memosWithTags.map { it.memo }
    
    val relations by viewModel.relations.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    
    var viewMode by remember { mutableStateOf(ViewMode.NETWORK) }
    var isGraphView by remember { mutableStateOf(true) }
    var selectedMemoId by remember { mutableStateOf<Long?>(null) }
    var detailMemoId by remember { mutableStateOf<Long?>(null) }
    
    var timeRangeStart by remember { mutableStateOf<Long?>(null) }
    var timeRangeEnd by remember { mutableStateOf<Long?>(null) }

    // ── 검색 관련 상태 ──
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // ── 마인드맵 그룹 선택 상태 ──
    // null = "전체" 보기(그룹별 클러스터 배치), String = 특정 태그 그룹만 표시
    var selectedMindmapGroup by remember { mutableStateOf<String?>(null) }

    val filteredMemosWithTags = remember(memosWithTags, timeRangeStart, timeRangeEnd, searchQuery) {
        var result = memosWithTags
        if (timeRangeStart != null && timeRangeEnd != null) {
            // Float 변환 정밀도 손실로 인한 경계값 누락 방지를 위해 1분의 오차 마진 버퍼 확보
            result = result.filter { it.memo.createdAt in (timeRangeStart!! - 60000L)..(timeRangeEnd!! + 60000L) }
        }
        val query = normalizeText(searchQuery)
        if (query.isNotEmpty()) {
            result = result.filter { item ->
                normalizeText(item.memo.content).contains(query, ignoreCase = true) ||
                item.tags.any { normalizeText(it.name).contains(query, ignoreCase = true) }
            }
        }
        result
    }

    // State for Quick Input Bar
    var content by remember { mutableStateOf("") }
    val context = LocalContext.current

    // ── 사용자 정의 커스텀 그룹 관리 ──
    val sharedPrefs = remember(context) { context.getSharedPreferences("dotnote_prefs", android.content.Context.MODE_PRIVATE) }
    var customGroups by remember { 
        mutableStateOf(
            sharedPrefs.getStringSet("custom_groups", emptySet()) ?: emptySet()
        )
    }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var isLocationEnabled by remember { mutableStateOf(false) }
    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        isLocationEnabled = granted
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            isLocationEnabled = true
        } else {
            permissionLauncher.launch(locationPermissions)
        }
    }

    // STT Launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0)?.trim() ?: ""
            if (spokenText.isNotEmpty()) {
                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasFine || hasCoarse) {
                    try {
                        locationClient.lastLocation.addOnSuccessListener { loc ->
                            if (loc != null) {
                                val randomLatOffset = (Math.random() - 0.5) * 0.04
                                val randomLngOffset = (Math.random() - 0.5) * 0.04
                                viewModel.saveMemo(
                                    content = spokenText,
                                    latitude = loc.latitude + randomLatOffset,
                                    longitude = loc.longitude + randomLngOffset
                                )
                            } else {
                                val randomLatOffset = (Math.random() - 0.5) * 0.04
                                val randomLngOffset = (Math.random() - 0.5) * 0.04
                                viewModel.saveMemo(
                                    content = spokenText,
                                    latitude = 37.5665 + randomLatOffset,
                                    longitude = 126.9780 + randomLngOffset
                                )
                            }
                        }.addOnFailureListener {
                            viewModel.saveMemo(content = spokenText)
                        }
                    } catch (e: SecurityException) {
                        viewModel.saveMemo(content = spokenText)
                    }
                } else {
                    viewModel.saveMemo(content = spokenText)
                }
            }
        }
    }

    // OCR Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val recognizedText = visionText.text.trim()
                    if (recognizedText.isNotEmpty()) {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            try {
                                locationClient.lastLocation.addOnSuccessListener { loc ->
                                    if (loc != null) {
                                        val randomLatOffset = (Math.random() - 0.5) * 0.04
                                        val randomLngOffset = (Math.random() - 0.5) * 0.04
                                        viewModel.saveMemo(
                                            content = recognizedText,
                                            latitude = loc.latitude + randomLatOffset,
                                            longitude = loc.longitude + randomLngOffset
                                        )
                                    } else {
                                        val randomLatOffset = (Math.random() - 0.5) * 0.04
                                        val randomLngOffset = (Math.random() - 0.5) * 0.04
                                        viewModel.saveMemo(
                                            content = recognizedText,
                                            latitude = 37.5665 + randomLatOffset,
                                            longitude = 126.9780 + randomLngOffset
                                        )
                                    }
                                }.addOnFailureListener {
                                    viewModel.saveMemo(content = recognizedText)
                                }
                            } catch (e: SecurityException) {
                                viewModel.saveMemo(content = recognizedText)
                            }
                        } else {
                            viewModel.saveMemo(content = recognizedText)
                        }
                    }
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

    // 노드의 위치를 보존하기 위한 캐시 (memoId → Node)
    // DB에서 메모 목록이 갱신되어도 기존 노드의 position/velocity를 유지
    val nodeCache = remember { mutableMapOf<Long, Node>() }

    val nodes = remember(memosWithTags, timeRangeStart, timeRangeEnd, searchQuery) {
        val updatedNodes = memosWithTags.map { item ->
            val memo = item.memo
            val tags = item.tags.map { it.name }
            // Float 변환 정밀도 손실로 인한 경계값 누락 방지를 위해 1분의 오차 마진 버퍼 확보
            val matchesTime = if (timeRangeStart == null || timeRangeEnd == null) true
                              else memo.createdAt in (timeRangeStart!! - 60000L)..(timeRangeEnd!! + 60000L)
            val query = normalizeText(searchQuery)
            val matchesSearch = if (query.isEmpty()) true
                                else normalizeText(memo.content).contains(query, ignoreCase = true) ||
                                     tags.any { normalizeText(it).contains(query, ignoreCase = true) }
            val isActive = matchesTime && matchesSearch
            val isHighlighted = query.isNotEmpty() && matchesSearch
            val isNew = (System.currentTimeMillis() - memo.createdAt) < 5 * 60 * 1000L
                           
            val existing = nodeCache[memo.id]
            if (existing != null) {
                // 기존 노드: 텍스트만 갱신하고 위치/속도는 보존
                existing.copy(text = memo.content, tags = tags, isActive = isActive, isHighlighted = isHighlighted, isNew = isNew).also {
                    it.position = existing.position
                    it.velocity = existing.velocity
                }
            } else {
                // 새 노드: 초기 위치는 Zero (레이아웃 엔진이 랜덤 배치)
                Node(id = memo.id, text = memo.content, position = Offset.Zero, tags = tags, isActive = isActive, isHighlighted = isHighlighted, isNew = isNew)
            }
        }
        // 캐시 갱신
        nodeCache.clear()
        updatedNodes.forEach { nodeCache[it.id] = it }
        updatedNodes
    }
    
    val edges = remember(relations, nodes) {
        val activeNodeIds = nodes.filter { it.isActive }.map { it.id }.toSet()
        relations.map { relation -> 
            val isSourceActive = relation.parentId in activeNodeIds
            val isTargetActive = relation.childId in activeNodeIds
            Edge(
                sourceId = relation.parentId, 
                targetId = relation.childId,
                isActive = isSourceActive && isTargetActive
            )
        }
    }

    // ── 마인드맵 그룹 관련 계산 ──
    // Edge(관계)로 연결된 메모들을 연결 컴포넌트(Connected Component)로 묶어 그룹핑.
    // 같은 태그를 공유하지 않아도 간접적으로 연결되면 같은 주제 클러스터로 인식됨.
    // 예: A(#여행)↔B(#여행,#관광)↔C(#관광) → A,B,C 모두 같은 그룹
    val groupLabels: Map<String, List<Long>> = remember(memosWithTags, relations) {
        val allIds = memosWithTags.map { it.memo.id }
        if (allIds.isEmpty()) return@remember emptyMap()

        // Union-Find로 연결 컴포넌트 계산
        val parent = mutableMapOf<Long, Long>()
        fun find(x: Long): Long {
            if (parent[x] != x) parent[x] = find(parent[x]!!)
            return parent[x]!!
        }
        fun union(a: Long, b: Long) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        allIds.forEach { parent[it] = it }
        relations.forEach { rel ->
            if (rel.parentId in parent && rel.childId in parent) {
                union(rel.parentId, rel.childId)
            }
        }

        // 컴포넌트별로 노드 ID 수집
        val components = mutableMapOf<Long, MutableList<Long>>()
        allIds.forEach { id ->
            val root = find(id)
            components.getOrPut(root) { mutableListOf() }.add(id)
        }

        // 각 컴포넌트의 대표 라벨: 해당 클러스터 내에서 가장 많이 등장하는 태그 이름 사용
        // 태그가 없는 고립 메모는 내용의 앞 8글자를 라벨로 사용
        val tagLookup = memosWithTags.associate { it.memo.id to it.tags.map { t -> t.name } }
        val contentLookup = memosWithTags.associate { it.memo.id to it.memo.content }
        val result = mutableMapOf<String, List<Long>>()

        components.values.forEach { memberIds ->
            // 클러스터 내 모든 태그를 수집하여 빈도순 정렬
            val tagFrequency = mutableMapOf<String, Int>()
            memberIds.forEach { id ->
                tagLookup[id]?.forEach { tag ->
                    tagFrequency[tag] = (tagFrequency[tag] ?: 0) + 1
                }
            }

            // 대표 라벨 결정: 사용자가 명시한 커스텀 그룹 태그가 클러스터에 존재한다면 최우선 적용
            val matchedCustomGroup = tagFrequency.keys
                .filter { it in customGroups }
                .maxByOrNull { tagFrequency[it] ?: 0 }

            val label = if (matchedCustomGroup != null) {
                matchedCustomGroup
            } else if (tagFrequency.isNotEmpty()) {
                tagFrequency.maxByOrNull { it.value }!!.key
            } else {
                // 태그 없는 고립 메모: 내용 앞부분을 라벨로
                val firstContent = contentLookup[memberIds.first()] ?: "메모"
                firstContent.take(8).trim()
            }

            // 동일 라벨이 이미 있으면 번호 접미사 추가 (충돌 방지)
            val uniqueLabel = if (label in result) {
                var suffix = 2
                while ("$label ($suffix)" in result) suffix++
                "$label ($suffix)"
            } else label

            result[uniqueLabel] = memberIds
        }

        result
    }

    // 그룹 목록 (칩 UI에 표시할 라벨들) — 커스텀 그룹을 목록에 항상 반영하여 노출
    val availableGroups: List<String> = remember(groupLabels, customGroups) {
        val calculated = groupLabels.entries.sortedByDescending { it.value.size }.map { it.key }
        (customGroups + calculated).distinct()
    }

    // 마인드맵 모드에서 선택된 그룹에 따라 필터링된 노드와 엣지
    val mindmapNodes = remember(nodes, selectedMindmapGroup, groupLabels, viewMode) {
        if (viewMode != ViewMode.MINDMAP || selectedMindmapGroup == null) {
            nodes // "전체" 모드이거나 네트워크 모드일 때는 전체 노드 표시
        } else {
            val groupNodeIds = groupLabels[selectedMindmapGroup]?.toSet() ?: emptySet()
            // 커스텀 그룹이 비어있거나 클러스터에 존재하지 않는 경우 개별 메모 태그 목록에 해당 그룹명이 있는지 확인
            val fallbackNodeIds = if (groupNodeIds.isEmpty()) {
                nodes.filter { selectedMindmapGroup in it.tags }.map { it.id }.toSet()
            } else groupNodeIds
            nodes.filter { it.id in fallbackNodeIds }
        }
    }

    val mindmapEdges = remember(edges, mindmapNodes, selectedMindmapGroup, viewMode) {
        if (viewMode != ViewMode.MINDMAP || selectedMindmapGroup == null) {
            edges
        } else {
            val nodeIdSet = mindmapNodes.map { it.id }.toSet()
            edges.filter { it.sourceId in nodeIdSet && it.targetId in nodeIdSet }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("메모 및 태그 검색...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear Search")
                                    }
                                }
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                TopAppBar(
                    title = { 
                        Text(
                            "DotNote", 
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        ) 
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        if (isGraphView) {
                            // Segmented Control for Network / MindMap
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Row(modifier = Modifier.padding(4.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (viewMode == ViewMode.NETWORK) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                        modifier = Modifier.clickable { viewMode = ViewMode.NETWORK }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share, 
                                            contentDescription = "Network Graph",
                                            tint = if (viewMode == ViewMode.NETWORK) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).size(20.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = if (viewMode == ViewMode.MINDMAP) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                        modifier = Modifier.clickable { viewMode = ViewMode.MINDMAP }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu, 
                                            contentDescription = "Mind Map",
                                            tint = if (viewMode == ViewMode.MINDMAP) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(onClick = onNavigateToMap) {
                            Icon(Icons.Default.Place, contentDescription = "Map View")
                        }
                        IconButton(onClick = { isGraphView = !isGraphView }) {
                            Icon(
                                imageVector = if (isGraphView) Icons.Default.List else Icons.Default.Share, 
                                contentDescription = "Toggle View"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            // Quick Capture Panel
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 24.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (isSaving) {
                        // AI Analysis Pulse and Text
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition()
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                )
                            )
                            Icon(
                                Icons.Default.Star, // AI sparkles
                                contentDescription = "AI Analyzing",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "AI is analyzing...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                            )
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Camera Button
                        IconButton(onClick = {
                            val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasCameraPermission) {
                                cameraLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // STT Button
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                            }
                            speechRecognizerLauncher.launch(intent)
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "Mic", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            placeholder = { Text("생각을 공유해 주세요!!") },
                            maxLines = 3,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        )

                        // Send Button
                        IconButton(
                            onClick = {
                                val rawContent = content
                                content = "" // UI 즉시 초기화하여 반응성 확보
                                if (rawContent.isBlank()) return@IconButton

                                // 마인드맵 특정 그룹 선택 시, 해당 그룹 태그 자동 부여
                                val memoContent = if (viewMode == ViewMode.MINDMAP && selectedMindmapGroup != null) {
                                    "$rawContent #$selectedMindmapGroup"
                                } else {
                                    rawContent
                                }

                                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (hasFine || hasCoarse) {
                                    try {
                                        locationClient.lastLocation.addOnSuccessListener { loc ->
                                            if (loc != null) {
                                                val randomLatOffset = (Math.random() - 0.5) * 0.04
                                                val randomLngOffset = (Math.random() - 0.5) * 0.04
                                                viewModel.saveMemo(
                                                    content = memoContent,
                                                    latitude = loc.latitude + randomLatOffset,
                                                    longitude = loc.longitude + randomLngOffset
                                                )
                                            } else {
                                                val randomLatOffset = (Math.random() - 0.5) * 0.04
                                                val randomLngOffset = (Math.random() - 0.5) * 0.04
                                                viewModel.saveMemo(
                                                    content = memoContent,
                                                    latitude = 37.5665 + randomLatOffset,
                                                    longitude = 126.9780 + randomLngOffset
                                                )
                                            }
                                        }.addOnFailureListener {
                                            viewModel.saveMemo(content = memoContent)
                                        }
                                    } catch (e: SecurityException) {
                                        viewModel.saveMemo(content = memoContent)
                                    }
                                } else {
                                    viewModel.saveMemo(content = memoContent)
                                }
                            },
                            enabled = content.isNotBlank() && !isSaving,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isGraphView) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        // 마인드맵 모드일 때는 필터링된 노드/엣지, 네트워크 모드일 때는 전체
                        val displayNodes = if (viewMode == ViewMode.MINDMAP) mindmapNodes else nodes
                        val displayEdges = if (viewMode == ViewMode.MINDMAP) mindmapEdges else edges
                        
                        GraphCanvas(
                            nodes = displayNodes,
                            edges = displayEdges,
                            viewMode = viewMode,
                            selectedNodeId = selectedMemoId,
                            onNodeClick = { id -> 
                                if (id == -1L) {
                                    selectedMemoId = null
                                } else {
                                    selectedMemoId = id
                                }
                            },
                            onNodeLongClick = { id ->
                                detailMemoId = id
                            },
                            // 마인드맵 그룹핑 파라미터
                            availableGroups = availableGroups,
                            selectedGroup = selectedMindmapGroup,
                            onGroupSelected = { group -> selectedMindmapGroup = group },
                            groupLabels = if (selectedMindmapGroup == null) groupLabels else null,
                            // 커스텀 그룹 설정
                            customGroups = customGroups,
                            onAddGroupClick = { showAddGroupDialog = true },
                            onRemoveGroupClick = { group ->
                                val updated = customGroups - group
                                customGroups = updated
                                sharedPrefs.edit().putStringSet("custom_groups", updated).apply()
                            }
                        )
                    }

                    // ── 커스텀 그룹 추가 다이얼로그 ──
                    if (showAddGroupDialog) {
                        AlertDialog(
                            onDismissRequest = { showAddGroupDialog = false },
                            title = { Text("새 그룹 태그 추가") },
                            text = {
                                OutlinedTextField(
                                    value = newGroupName,
                                    onValueChange = { newGroupName = it },
                                    label = { Text("그룹 태그 이름") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val cleanName = newGroupName.trim().replace(Regex("[^가-힣a-zA-Z0-9]"), "")
                                        if (cleanName.isNotEmpty()) {
                                            val updated = customGroups + cleanName
                                            customGroups = updated
                                            sharedPrefs.edit().putStringSet("custom_groups", updated).apply()
                                            newGroupName = ""
                                            showAddGroupDialog = false
                                        }
                                    }
                                ) {
                                    Text("추가")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        newGroupName = ""
                                        showAddGroupDialog = false
                                    }
                                ) {
                                    Text("취소")
                                }
                            }
                        )
                    }
                    
                    val minTime = remember(memosWithTags) {
                        memosWithTags.minOfOrNull { it.memo.createdAt } 
                            ?: (System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
                    }
                    val maxTime = remember(memosWithTags, minTime) {
                        val dbMax = memosWithTags.maxOfOrNull { it.memo.createdAt } ?: System.currentTimeMillis()
                        val minRange = minTime + 24 * 60 * 60 * 1000L
                        java.lang.Long.max(dbMax, minRange)
                    }
                    
                    TimelineSlider(
                        minTime = minTime,
                        maxTime = maxTime,
                        memos = memosWithTags.map { it.memo },
                        onTimeRangeChanged = { start, end ->
                            timeRangeStart = start
                            timeRangeEnd = end
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMemosWithTags.map { it.memo }) { memo ->
                        val isNew = (System.currentTimeMillis() - memo.createdAt) < 5 * 60 * 1000L
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { selectedMemoId = memo.id },
                                    onLongClick = { detailMemoId = memo.id }
                                )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = memo.content,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isNew) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = androidx.compose.ui.graphics.Color(0xFF2ECC71).copy(alpha = 0.15f),
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = "NEW",
                                                color = androidx.compose.ui.graphics.Color(0xFF27AE60),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Bottom Sheet
    detailMemoId?.let { id ->
        val selectedMemoWithTags = memosWithTags.find { it.memo.id == id }
        if (selectedMemoWithTags != null) {
            val selectedMemo = selectedMemoWithTags.memo
            val tags = selectedMemoWithTags.tags.map { it.name }
            // 현재 메모와 연결된 Edge(Relation)를 통해 다른 메모와 연결된 이유(Tag 등) 추출
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
                onClose = { detailMemoId = null },
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
                    detailMemoId = null
                },
                tags = tags,
                relatedMemos = relatedMemosList
            )
        }
    }
}
