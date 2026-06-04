package com.cookandroid.dotnote.presentation.canvas

import androidx.compose.ui.geometry.Offset

data class Node(
    val id: Long,
    val text: String,
    var position: Offset = Offset.Zero,
    var velocity: Offset = Offset.Zero, // For force-directed layout
    var mass: Float = 1f,
    var isDragging: Boolean = false,
    val tags: List<String> = emptyList(),
    val isActive: Boolean = true,
    val isHighlighted: Boolean = false,
    val isNew: Boolean = false
)

data class Edge(
    val sourceId: Long,
    val targetId: Long,
    val type: String = "related",
    val isActive: Boolean = true
)
