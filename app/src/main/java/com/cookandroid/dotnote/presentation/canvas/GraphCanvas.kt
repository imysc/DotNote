package com.cookandroid.dotnote.presentation.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

enum class ViewMode {
    NETWORK, MINDMAP
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun GraphCanvas(
    nodes: List<Node>,
    edges: List<Edge>,
    viewMode: ViewMode = ViewMode.NETWORK,
    selectedNodeId: Long? = null,
    onNodeClick: (Long) -> Unit,
    // ── 마인드맵 그룹핑 관련 파라미터 ──
    availableGroups: List<String> = emptyList(),
    selectedGroup: String? = null,
    onGroupSelected: (String?) -> Unit = {},
    groupLabels: Map<String, List<Long>>? = null,
    // ── 커스텀 그룹 태그 추가/삭제 콜백 ──
    customGroups: Set<String> = emptySet(),
    onAddGroupClick: () -> Unit = {},
    onRemoveGroupClick: (String) -> Unit = {}
) {
    // 화면 좌표계에서의 변환 상태
    // offset = 화면 픽셀 단위의 이동량, scale = 확대 배율
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val graphLayoutEngine = remember { GraphLayoutEngine() }
    val mindMapLayoutEngine = remember { MindMapLayoutEngine() }
    val textMeasurer = rememberTextMeasurer()

    var canvasBounds by remember { mutableStateOf(Pair(1000f, 1000f)) }

    // 노드/엣지 또는 뷰 모드가 바뀌면 시뮬레이션 재시작
    LaunchedEffect(nodes, edges, viewMode, selectedGroup) {
        graphLayoutEngine.invalidate()
        mindMapLayoutEngine.invalidate()
    }

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    
    // 물리 시뮬레이션 루프 (안정화되면 자동 정지)
    LaunchedEffect(nodes, edges, viewMode, canvasBounds, density, selectedGroup, groupLabels) {
        while (true) {
            when (viewMode) {
                ViewMode.NETWORK -> graphLayoutEngine.calculateLayout(nodes, edges, canvasBounds, density)
                ViewMode.MINDMAP -> mindMapLayoutEngine.calculateLayout(
                    nodes, edges, canvasBounds, null, density,
                    // "전체" 모드일 때만 그룹별 클러스터 배치 사용
                    groupLabels = if (selectedGroup == null) groupLabels else null,
                    selectedGroup = selectedGroup
                )
            }
            delay(16L)
            
            // 현재 활성화된 모드의 엔진 안정화 상태 확인
            val isCurrentStable = if (viewMode == ViewMode.NETWORK) graphLayoutEngine.isStable else mindMapLayoutEngine.isStable
            if (isCurrentStable) {
                delay(500L)
            }
        }
    }

    // 줌 레벨에 따라 노드 크기와 표시 글자 수를 동적으로 결정
    // 확대할수록 카드가 커지고 더 많은 텍스트가 보임
    // 노드 간 간격 및 텍스트 표시 확보를 위해 노드 기본 크기 확대
    val baseNodeWidth = 240f
    val baseNodeHeight = 90f

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val outlineColor = MaterialTheme.colorScheme.outline
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // 핀치 줌 & 팬 제스처
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = scale
                        val newScale = (oldScale * zoom).coerceIn(0.3f, 5f)

                        // 핀치 중심을 기준으로 줌 (구글 맵처럼 자연스러운 줌)
                        val focusX = centroid.x
                        val focusY = centroid.y
                        offset = Offset(
                            focusX - (focusX - offset.x) * (newScale / oldScale) + pan.x,
                            focusY - (focusY - offset.y) * (newScale / oldScale) + pan.y
                        )
                        scale = newScale
                    }
                }
                // 탭 제스처 (노드 클릭)
                .pointerInput(nodes, scale, offset) {
                    detectTapGestures { tapScreenPos ->
                        val worldPos = Offset(
                            (tapScreenPos.x - offset.x) / scale,
                            (tapScreenPos.y - offset.y) / scale
                        )

                        // 히트 영역을 넉넉하게 잡아 터치 정확도를 높임
                        val hitPadding = 20f
                        val clickedNode = nodes.find { node ->
                            abs(node.position.x - worldPos.x) <= (baseNodeWidth / 2 + hitPadding) &&
                            abs(node.position.y - worldPos.y) <= (baseNodeHeight / 2 + hitPadding)
                        }
                        if (clickedNode != null) {
                            onNodeClick(clickedNode.id)
                        }
                    }
                }
        ) {
            canvasBounds = Pair(size.width, size.height)

            // 노트 줄선 스타일 배경 (가로줄만 = 공책 느낌)
            val lineSpacing = 32.dp.toPx()
            val lineColor = outlineColor.copy(alpha = 0.12f)
            
            for (y in 0..size.height.toInt() step lineSpacing.toInt()) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
            
            // 좌측 여백선 (빨간 줄, 노트 스타일)
            drawLine(
                color = Color(0x20CC6B5A),
                start = Offset(48.dp.toPx(), 0f),
                end = Offset(48.dp.toPx(), size.height),
                strokeWidth = 1.5f
            )

            // Canvas 내부에서 직접 translate/scale 변환 적용
            // graphicsLayer 대신 drawScope 변환을 사용하면 텍스트가 선명하게 유지됨
            withTransform({
                translate(left = offset.x, top = offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {

                // ── 간선(Edge) 그리기 ──
                // 마인드맵 모드에서는 BFS 스패닝 트리 엣지만 그려서 깨끗한 트리 형태 유지
                val currentTreeEdges = mindMapLayoutEngine.treeEdges
                
                edges.forEach { edge ->
                    // 마인드맵 모드: 트리 엣지가 아닌 교차 연결은 건너뛴다
                    if (viewMode == ViewMode.MINDMAP && currentTreeEdges.isNotEmpty()) {
                        val isTreeEdge = Pair(edge.sourceId, edge.targetId) in currentTreeEdges
                        if (!isTreeEdge) return@forEach
                    }
                    
                    val src = nodes.find { it.id == edge.sourceId }
                    val tgt = nodes.find { it.id == edge.targetId }
                    if (src != null && tgt != null) {
                        val midX = (src.position.x + tgt.position.x) / 2f
                        val midY = (src.position.y + tgt.position.y) / 2f
                        val curveOffset = (src.position.x - tgt.position.y) * 0.08f

                        val path = Path().apply {
                            moveTo(src.position.x, src.position.y)
                            quadraticBezierTo(
                                midX + curveOffset,
                                midY - curveOffset,
                                tgt.position.x,
                                tgt.position.y
                            )
                        }
                        val edgeAlpha = if (edge.isActive) 0.65f else 0.1f
                        val circleAlpha = if (edge.isActive) 0.5f else 0.08f

                        drawPath(
                            path = path,
                            color = primaryColor.copy(alpha = edgeAlpha),
                            style = Stroke(width = 4f)
                        )

                        drawCircle(
                            color = primaryColor.copy(alpha = circleAlpha),
                            radius = 6f,
                            center = Offset(midX + curveOffset * 0.5f, midY - curveOffset * 0.5f)
                        )
                    }
                }

                // ── 노드(Node) 그리기 ──
                nodes.forEach { node ->
                    // 사용자가 요청한 대로 크기를 약간 축소
                    val nw = 130.dp.toPx()
                    val nh = 65.dp.toPx()
                    val left = node.position.x - nw / 2
                    val top = node.position.y - nh / 2
                    val cr = 14.dp.toPx()

                    val isSelected = selectedNodeId == node.id

                    // 그림자
                    val shadowPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left + 2.dp.toPx(), top = top + 3.dp.toPx(),
                                right = left + nw + 2.dp.toPx(), bottom = top + nh + 3.dp.toPx(),
                                cornerRadius = CornerRadius(cr)
                            )
                        )
                    }
                    val nodeAlpha = if (node.isActive) 1f else 0.2f
                    
                    // 부드러운 종이 위 그림자
                    val shadowBaseAlpha = if (isSelected) 0.2f else 0.08f
                    val shadowColor = if (isSelected) primaryColor else Color.Black
                    drawPath(path = shadowPath, color = shadowColor.copy(alpha = shadowBaseAlpha * nodeAlpha))

                    // 카드 배경
                    val cardPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left, top = top,
                                right = left + nw, bottom = top + nh,
                                cornerRadius = CornerRadius(cr)
                            )
                        )
                    }
                    val cardColor = if (isSelected) primaryContainerColor else surfaceColor
                    drawPath(path = cardPath, color = cardColor.copy(alpha = nodeAlpha))

                    // 검색 매칭 노드에 옐로우-골드 틴트 레이어 추가 (부드럽게 겹쳐서 다크/라이트 모두 어우러지게 함)
                    if (node.isHighlighted) {
                        drawPath(path = cardPath, color = Color(0x33FFD54F).copy(alpha = nodeAlpha))
                    }

                    // 신규 노드에 에메랄드-그린 틴트 레이어 추가 (부드러운 네온 그린 틴팅)
                    if (node.isNew) {
                        drawPath(path = cardPath, color = Color(0x282ECC71).copy(alpha = nodeAlpha))
                    }

                    // 카드 테두리
                    val borderWidth = when {
                        isSelected -> 2.5f.dp.toPx()
                        node.isHighlighted -> 3.dp.toPx() // 검색 매칭 시 더 굵은 테두리
                        node.isNew -> 2.dp.toPx() // 신규 노드도 약간 더 굵은 테두리
                        else -> 1.dp.toPx()
                    }
                    val borderColor = when {
                        isSelected -> primaryColor
                        node.isHighlighted -> Color(0xFFFFB300) // 선명한 골드 옐로우 색상 테두리
                        node.isNew -> Color(0xFF2ECC71) // 선명한 에메랄드 그린 색상 테두리
                        else -> primaryColor.copy(alpha = 0.5f)
                    }
                    drawPath(
                        path = cardPath,
                        color = borderColor.copy(alpha = borderColor.alpha * nodeAlpha),
                        style = Stroke(width = borderWidth)
                    )

                    // 좌측 컬러 액센트 바
                    val accentBarColor = if (node.isNew) Color(0xFF2ECC71) else primaryColor
                    drawRoundRect(
                        color = accentBarColor.copy(alpha = nodeAlpha),
                        topLeft = Offset(left, top),
                        size = Size(6.dp.toPx(), nh),
                        cornerRadius = CornerRadius(cr, cr)
                    )

                    // ── 텍스트 설정 ──
                    val charLimit = 30
                    val maxLines = 2
                    val fontSize = 12.sp

                    val previewText = node.text.take(charLimit) +
                            if (node.text.length > charLimit) "…" else ""

                    val textColor = if (isSelected) onPrimaryContainerColor else onSurfaceColor
                    val textLayoutResult = textMeasurer.measure(
                        text = previewText,
                        style = TextStyle(
                            color = textColor.copy(alpha = nodeAlpha),
                            fontSize = fontSize,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 16.sp
                        ),
                        maxLines = maxLines,
                        overflow = TextOverflow.Ellipsis,
                        constraints = androidx.compose.ui.unit.Constraints(
                            maxWidth = (nw - 24.dp.toPx()).toInt()
                        )
                    )

                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(
                            left + 14.dp.toPx(),
                            top + 8.dp.toPx()
                        )
                    )

                    // Draw Tags (just the first one for preview)
                    if (node.tags.isNotEmpty()) {
                        val tagText = "#" + node.tags.first()
                        val tagLayoutResult = textMeasurer.measure(
                            text = tagText,
                            style = TextStyle(
                                color = primaryColor.copy(alpha = nodeAlpha),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            constraints = androidx.compose.ui.unit.Constraints(
                                maxWidth = (nw - 24.dp.toPx()).toInt()
                            )
                        )
                        
                        // Background pill for tag
                        val tagWidth = tagLayoutResult.size.width + 12.dp.toPx()
                        val tagHeight = tagLayoutResult.size.height + 6.dp.toPx()
                        val tagY = top + nh - tagHeight - 6.dp.toPx()
                        
                        drawRoundRect(
                            color = primaryColor.copy(alpha = if (node.isActive) 0.1f else 0.1f * nodeAlpha),
                            topLeft = Offset(left + 14.dp.toPx(), tagY),
                            size = Size(tagWidth, tagHeight),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                        
                        drawText(
                            textLayoutResult = tagLayoutResult,
                            topLeft = Offset(left + 20.dp.toPx(), tagY + 3.dp.toPx())
                        )
                    }

                    // 신규 추가된 노드라면 우측 상단에 'NEW' 알약 배지 추가
                    if (node.isNew) {
                        val badgeText = "NEW"
                        val badgeLayout = textMeasurer.measure(
                            text = badgeText,
                            style = TextStyle(
                                color = Color.White.copy(alpha = nodeAlpha),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        val badgeW = badgeLayout.size.width + 10.dp.toPx()
                        val badgeH = badgeLayout.size.height + 4.dp.toPx()
                        val badgeX = left + nw - badgeW - 8.dp.toPx()
                        val badgeY = top + 6.dp.toPx()
                        
                        drawRoundRect(
                            color = Color(0xFF2ECC71).copy(alpha = nodeAlpha),
                            topLeft = Offset(badgeX, badgeY),
                            size = Size(badgeW, badgeH),
                            cornerRadius = CornerRadius(50f, 50f)
                        )
                        
                        drawText(
                            textLayoutResult = badgeLayout,
                            topLeft = Offset(badgeX + 5.dp.toPx(), badgeY + 2.dp.toPx())
                        )
                    }
                }
            }
        }

        // ── 마인드맵 모드: 그룹 선택 칩 UI (상단) ──
        if (viewMode == ViewMode.MINDMAP) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "전체" 칩
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selectedGroup == null) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onGroupSelected(null) }
                    ) {
                        Text(
                            text = "전체",
                            color = if (selectedGroup == null) onPrimaryColor else onSurfaceColor,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // 태그별 그룹 칩
                    availableGroups.forEach { group ->
                        val isCustom = group in customGroups
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (selectedGroup == group) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { onGroupSelected(group) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "#$group",
                                    color = if (selectedGroup == group) onPrimaryColor else onSurfaceColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (isCustom) {
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { 
                                                onRemoveGroupClick(group)
                                                if (selectedGroup == group) {
                                                    onGroupSelected(null)
                                                }
                                            }
                                    ) {
                                        Text(
                                            text = "✕",
                                            color = (if (selectedGroup == group) onPrimaryColor else onSurfaceColor).copy(alpha = 0.7f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // "+ 추가" 칩
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { onAddGroupClick() }
                    ) {
                        Text(
                            text = "+ 그룹 추가",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // ── 줌 힌트 (우측 하단) ──
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text(
                text = "Pinch to zoom ${(scale * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
