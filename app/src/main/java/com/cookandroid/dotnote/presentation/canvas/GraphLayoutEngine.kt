package com.cookandroid.dotnote.presentation.canvas

import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class GraphLayoutEngine {
    private val repulsionConstant = 350000f // 척력 대폭 증가 (노드 간 간격 확보)
    private val springLength = 350f // 연결선 기본 길이 증가
    private val springConstant = 0.04f
    private val friction = 0.80f

    // 에너지가 이 임계값 아래로 내려가면 시뮬레이션 정지
    private val energyThreshold = 0.5f
    var isStable = false
        private set

    fun calculateLayout(nodes: List<Node>, edges: List<Edge>, bounds: Pair<Float, Float>, density: Float = 1f) {
        if (nodes.isEmpty()) return

        // 이미 안정화된 상태라면 계산 스킵
        if (isStable) return

        val width = bounds.first
        val height = bounds.second
        
        // 노드 크기가 커진 만큼 밀도에 비례하여 척력과 스프링 길이를 크게 증가
        // 밀도가 3(최신 스마트폰 수준)일 경우 훨씬 넓게 펴지도록 만듦
        val activeRepulsion = repulsionConstant * density * density * 2f
        val activeSpringLength = springLength * density * 1.5f

        // 초기 위치가 아직 잡히지 않은 노드만 랜덤 배치
        nodes.forEach { node ->
            if (node.position == Offset.Zero && !node.isDragging) {
                node.position = Offset(
                    width * 0.2f + (Math.random() * width * 0.6f).toFloat(),
                    height * 0.2f + (Math.random() * height * 0.6f).toFloat()
                )
            }
        }

        // 힘 계산
        val forces = HashMap<Long, Offset>()

        // 1. 척력 (모든 노드 쌍)
        for (i in nodes.indices) {
            val node1 = nodes[i]
            var forceX = 0f
            var forceY = 0f

            for (j in nodes.indices) {
                if (i == j) continue
                val node2 = nodes[j]

                var dx = node1.position.x - node2.position.x
                var dy = node1.position.y - node2.position.y
                
                // 두 노드의 좌표가 완전히 겹친 경우 랜덤하게 약간 어긋나게 함 (0으로 나누기 방지 및 척력 활성화)
                if (dx == 0f && dy == 0f) {
                    dx = (Math.random() - 0.5f).toFloat() * 10f
                    dy = (Math.random() - 0.5f).toFloat() * 10f
                }

                val distanceSq = max(1f, dx * dx + dy * dy)
                val distance = sqrt(distanceSq)

                val repulsion = activeRepulsion / distanceSq
                forceX += (dx / distance) * repulsion
                forceY += (dy / distance) * repulsion
            }
            forces[node1.id] = Offset(forceX, forceY)
        }

        // 2. 인력 (연결된 Edge만)
        for (edge in edges) {
            val node1 = nodes.find { it.id == edge.sourceId } ?: continue
            val node2 = nodes.find { it.id == edge.targetId } ?: continue

            val dx = node2.position.x - node1.position.x
            val dy = node2.position.y - node1.position.y
            val distance = max(1f, sqrt(dx * dx + dy * dy))

            val displacement = distance - activeSpringLength
            val force = displacement * springConstant

            val fx = (dx / distance) * force
            val fy = (dy / distance) * force

            forces[node1.id] = (forces[node1.id] ?: Offset.Zero) + Offset(fx, fy)
            forces[node2.id] = (forces[node2.id] ?: Offset.Zero) - Offset(fx, fy)
        }

        // 3. 위치 업데이트 및 총 에너지 계산
        var totalEnergy = 0f

        for (node in nodes) {
            if (node.isDragging) continue

            val force = forces[node.id] ?: Offset.Zero

            node.velocity = (node.velocity + force) * friction

            var newX = node.position.x + node.velocity.x
            var newY = node.position.y + node.velocity.y

            // 물리적 경계(Boundary)에 부딪혀 모서리에 겹치는 현상을 방지하기 위해 강제 위치 고정(clamp)을 제거.
            // 대신 화면 중앙으로 아주 약하게 당기는 중력(Gravity)을 주어 멀리 날아가지 않고 모이도록 유도.
            val cx = width / 2f
            val cy = height / 2f
            newX += (cx - newX) * 0.005f
            newY += (cy - newY) * 0.005f

            node.position = Offset(newX, newY)

            // 운동 에너지 누적
            totalEnergy += node.velocity.x * node.velocity.x + node.velocity.y * node.velocity.y
        }

        // 에너지가 충분히 낮으면 안정 상태로 전환
        isStable = totalEnergy < energyThreshold
    }

    /**
     * 노드/엣지가 변경되었을 때 시뮬레이션 재시작
     */
    fun invalidate() {
        isStable = false
    }
}
