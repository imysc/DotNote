package com.cookandroid.dotnote.presentation.canvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 마인드맵 전용 방사형(Radial-Tree) 레이아웃 엔진.
 *
 * 핵심 알고리즘: "섹터 기반 방사형 배치"
 * - 루트 노드를 중심에 놓고, 각 자식 노드에 부채꼴(Sector)을 할당한다.
 * - 자식 노드의 하위 노드는 부모의 섹터 범위 안에서만 배치된다.
 * - 결과: 가지(Branch)가 겹치지 않고 자연스럽게 바깥으로 뻗어나가는 마인드맵 형태.
 *
 * 다중 클러스터 모드:
 * - groupLabels가 주어지면 그룹별로 분리된 미니 마인드맵을 한 화면에 배치한다.
 */
class MindMapLayoutEngine {

    var isStable = false
        private set

    /** BFS 스패닝 트리의 엣지만 저장 (parentId, childId).
     *  마인드맵에서는 이 엣지만 그려야 깨끗한 트리 형태가 유지된다. */
    var treeEdges: Set<Pair<Long, Long>> = emptySet()
        private set

    /**
     * @param nodes      렌더링할 노드 목록
     * @param edges      노드 간 연결 정보
     * @param bounds     캔버스 크기 (width, height)
     * @param rootId     지정 루트 (null이면 자동 선택)
     * @param density    화면 밀도 (px/dp)
     * @param groupLabels 그룹별 노드 ID 맵 (null이면 단일 트리 모드)
     */
    fun calculateLayout(
        nodes: List<Node>,
        edges: List<Edge>,
        bounds: Pair<Float, Float>,
        rootId: Long?,
        density: Float = 1f,
        groupLabels: Map<String, List<Long>>? = null
    ) {
        if (nodes.isEmpty()) {
            isStable = true
            treeEdges = emptySet()
            return
        }
        if (isStable) return

        val width = bounds.first
        val height = bounds.second

        // 레이아웃 계산 전에 트리 엣지 초기화
        treeEdges = emptySet()

        val targetPositions: Map<Long, Offset> = if (groupLabels != null && groupLabels.size > 1) {
            // ── 다중 클러스터 모드: 그룹별 미니 마인드맵을 격자 배치 ──
            calculateMultiClusterLayout(nodes, edges, width, height, density, groupLabels)
        } else {
            // ── 단일 트리 모드: 전체 노드를 하나의 방사형 트리로 배치 ──
            calculateSingleTreeLayout(nodes, edges, width, height, density, rootId)
        }

        // 현재 위치에서 목표 위치로 스프링 보간 이동
        var maxDisplacement = 0f

        nodes.forEach { node ->
            if (!node.isDragging) {
                val target = targetPositions[node.id] ?: return@forEach
                val current = node.position

                if (current == Offset.Zero) {
                    // 첫 배치: 즉시 이동
                    node.position = target
                    maxDisplacement = maxOf(maxDisplacement, 100f)
                } else {
                    val dx = target.x - current.x
                    val dy = target.y - current.y

                    // 매 프레임 남은 거리의 12%씩 이동 (스프링 효과)
                    node.position = Offset(
                        current.x + dx * 0.12f,
                        current.y + dy * 0.12f
                    )

                    val displacement = sqrt(dx * dx + dy * dy)
                    maxDisplacement = maxOf(maxDisplacement, displacement)
                }
            }
        }

        // 모든 노드가 목표 위치에 거의 도달(1px 이내)하면 시뮬레이션 안정화
        isStable = maxDisplacement < 1f
    }

    // ─────────────────────────────────────────────────────
    //  단일 트리 모드: 섹터 기반 방사형 배치
    // ─────────────────────────────────────────────────────

    private fun calculateSingleTreeLayout(
        nodes: List<Node>,
        edges: List<Edge>,
        width: Float,
        height: Float,
        density: Float,
        rootId: Long?
    ): Map<Long, Offset> {
        val nodeMap = nodes.associateBy { it.id }

        // 루트 노드 선택: 지정값 → 연결 수 최다 → 첫 번째 노드
        val actualRootId = rootId ?: nodes.maxByOrNull { node ->
            edges.count { it.sourceId == node.id || it.targetId == node.id }
        }?.id ?: nodes.first().id

        // 무방향 인접 리스트
        val adjacency = buildAdjacency(edges)

        // BFS로 트리 구조 구축 (parentMap, childrenMap)
        // 이 BFS 과정에서 생성되는 엣지만이 마인드맵의 "가지"가 된다.
        val parentMap = mutableMapOf<Long, Long>()     // childId → parentId
        val childrenMap = mutableMapOf<Long, MutableList<Long>>()
        val localTreeEdges = mutableSetOf<Pair<Long, Long>>()
        val visited = mutableSetOf(actualRootId)
        val bfsQueue = ArrayDeque<Long>()
        bfsQueue.add(actualRootId)

        while (bfsQueue.isNotEmpty()) {
            val current = bfsQueue.removeFirst()
            adjacency[current]?.forEach { neighbor ->
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    parentMap[neighbor] = current
                    childrenMap.getOrPut(current) { mutableListOf() }.add(neighbor)
                    // 트리 엣지 기록 (양방향으로 저장하여 검색 용이)
                    localTreeEdges.add(Pair(current, neighbor))
                    localTreeEdges.add(Pair(neighbor, current))
                    bfsQueue.add(neighbor)
                }
            }
        }

        // 트리 엣지를 엔진 프로퍼티로 노출
        treeEdges = treeEdges + localTreeEdges

        // 고립된 노드(엣지 없음) 수집
        val isolatedNodes = nodes.filter { it.id !in visited }

        // 각 노드의 "리프 후손 수(SubtreeSize)"를 구해, 섹터 크기를 결정하는 가중치로 활용
        val subtreeSize = mutableMapOf<Long, Int>()
        fun computeSubtreeSize(nodeId: Long): Int {
            val children = childrenMap[nodeId]
            if (children.isNullOrEmpty()) {
                subtreeSize[nodeId] = 1
                return 1
            }
            val total = children.sumOf { computeSubtreeSize(it) }
            subtreeSize[nodeId] = total
            return total
        }
        computeSubtreeSize(actualRootId)

        // ── 섹터 기반 좌표 계산 ──
        val targetPositions = mutableMapOf<Long, Offset>()
        val centerX = width / 2f
        val centerY = height / 2f
        // 레벨 간 반지름 기본값 (밀도 반영)
        val levelRadius = 280f * density

        targetPositions[actualRootId] = Offset(centerX, centerY)

        // DFS로 각 노드에 (시작 각도, 끝 각도) 범위를 재귀 할당
        fun layoutSubtree(nodeId: Long, level: Int, startAngle: Double, endAngle: Double) {
            val children = childrenMap[nodeId] ?: return
            val totalWeight = children.sumOf { subtreeSize[it] ?: 1 }
            val radius = level * levelRadius

            var currentAngle = startAngle

            children.forEach { childId ->
                val weight = (subtreeSize[childId] ?: 1).toDouble()
                // 부모의 섹터를 자식의 SubtreeSize 비율로 분배
                val sectorSpan = (endAngle - startAngle) * (weight / totalWeight)
                val midAngle = currentAngle + sectorSpan / 2.0

                val tx = centerX + (radius * cos(midAngle)).toFloat()
                val ty = centerY + (radius * sin(midAngle)).toFloat()
                targetPositions[childId] = Offset(tx, ty)

                // 자식의 하위 트리는 할당받은 섹터 범위 안에서만 배치
                layoutSubtree(childId, level + 1, currentAngle, currentAngle + sectorSpan)

                currentAngle += sectorSpan
            }
        }

        // 루트의 직속 자식들을 360도(2π) 전체에 걸쳐 배치
        layoutSubtree(actualRootId, 1, 0.0, 2 * PI)

        // 고립 노드: 하단에 수평 나열
        if (isolatedNodes.isNotEmpty()) {
            val yPos = centerY + (((subtreeSize[actualRootId] ?: 1) / 3) + 2) * levelRadius
            val spacing = min(300f * density, width / (isolatedNodes.size + 1))
            val startX = centerX - (isolatedNodes.size - 1) * spacing / 2f

            isolatedNodes.forEachIndexed { index, node ->
                targetPositions[node.id] = Offset(startX + index * spacing, yPos)
            }
        }

        return targetPositions
    }

    // ─────────────────────────────────────────────────────
    //  다중 클러스터 모드: 그룹별 미니 마인드맵을 격자 배치
    // ─────────────────────────────────────────────────────

    private fun calculateMultiClusterLayout(
        nodes: List<Node>,
        edges: List<Edge>,
        width: Float,
        height: Float,
        density: Float,
        groupLabels: Map<String, List<Long>>
    ): Map<Long, Offset> {
        val targetPositions = mutableMapOf<Long, Offset>()
        val groups = groupLabels.entries.toList()

        // 격자 배치를 위한 열/행 수 결정
        val cols = kotlin.math.ceil(sqrt(groups.size.toDouble())).toInt().coerceAtLeast(1)
        val rows = kotlin.math.ceil(groups.size.toDouble() / cols).toInt().coerceAtLeast(1)

        val cellWidth = width / cols
        val cellHeight = height / rows

        groups.forEachIndexed { index, (_, nodeIds) ->
            val col = index % cols
            val row = index / cols

            // 각 셀의 중심 좌표
            val cellCenterX = cellWidth * col + cellWidth / 2f
            val cellCenterY = cellHeight * row + cellHeight / 2f

            // 이 그룹에 속한 노드와 엣지만 추출
            val nodeIdSet = nodeIds.toSet()
            val groupNodes = nodes.filter { it.id in nodeIdSet }
            val groupEdges = edges.filter { it.sourceId in nodeIdSet && it.targetId in nodeIdSet }

            if (groupNodes.isEmpty()) return@forEachIndexed

            // 그룹 내 루트: 연결이 가장 많은 노드
            val groupRootId = groupNodes.maxByOrNull { node ->
                groupEdges.count { it.sourceId == node.id || it.targetId == node.id }
            }?.id ?: groupNodes.first().id

            // 그룹별 섹터 레이아웃 계산 (셀 크기 기반)
            val groupPositions = calculateSingleTreeLayout(
                groupNodes, groupEdges, cellWidth, cellHeight, density * 0.6f, groupRootId
            )

            // 셀 좌표계로 변환 (원점을 셀 중심으로 이동)
            val offsetX = cellCenterX - cellWidth / 2f
            val offsetY = cellCenterY - cellHeight / 2f

            groupPositions.forEach { (id, pos) ->
                targetPositions[id] = Offset(pos.x + offsetX, pos.y + offsetY)
            }
        }

        // 어떤 그룹에도 속하지 않은 노드 처리
        val assignedIds = groupLabels.values.flatten().toSet()
        val unassigned = nodes.filter { it.id !in assignedIds }
        if (unassigned.isNotEmpty()) {
            val yPos = height - 100f * density
            val spacing = min(250f * density, width / (unassigned.size + 1))
            val startX = width / 2f - (unassigned.size - 1) * spacing / 2f
            unassigned.forEachIndexed { idx, node ->
                targetPositions[node.id] = Offset(startX + idx * spacing, yPos)
            }
        }

        return targetPositions
    }

    // ─────────────────────────────────────────────────────
    //  유틸리티
    // ─────────────────────────────────────────────────────

    /** 무방향 인접 리스트 생성 */
    private fun buildAdjacency(edges: List<Edge>): Map<Long, List<Long>> {
        val adj = mutableMapOf<Long, MutableList<Long>>()
        edges.forEach { edge ->
            adj.getOrPut(edge.sourceId) { mutableListOf() }.add(edge.targetId)
            adj.getOrPut(edge.targetId) { mutableListOf() }.add(edge.sourceId)
        }
        return adj
    }

    fun invalidate() {
        isStable = false
    }
}
