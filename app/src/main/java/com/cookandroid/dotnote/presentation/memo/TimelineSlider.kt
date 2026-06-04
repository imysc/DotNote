package com.cookandroid.dotnote.presentation.memo

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.cookandroid.dotnote.data.local.entity.MemoEntity

@Composable
fun TimelineSlider(
    minTime: Long,
    maxTime: Long,
    memos: List<MemoEntity> = emptyList(),
    onTimeRangeChanged: (Long, Long) -> Unit
) {
    var sliderPosition by remember { mutableStateOf(minTime.toFloat()..maxTime.toFloat()) }
    val formatter = remember { SimpleDateFormat("MM.dd", Locale.getDefault()) }

    // Surface for glassmorphism effect
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatter.format(Date(sliderPosition.start.toLong())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatter.format(Date(sliderPosition.endInclusive.toLong())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                // Heatmap Background
                val primaryColor = MaterialTheme.colorScheme.primary
                Canvas(modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp)) {
                    val width = size.width
                    val height = size.height
                    
                    // Draw base track
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.1f),
                        size = Size(width, height),
                        cornerRadius = CornerRadius(height / 2, height / 2)
                    )
                    
                    // Draw density bars
                    if (memos.isNotEmpty() && maxTime > minTime) {
                        val timeRange = maxTime - minTime
                        val bucketCount = 40
                        val buckets = IntArray(bucketCount)
                        
                        memos.forEach { memo ->
                            val t = memo.createdAt.coerceIn(minTime, maxTime)
                            val ratio = (t - minTime).toFloat() / timeRange.toFloat()
                            val idx = (ratio * (bucketCount - 1)).toInt()
                            buckets[idx]++
                        }
                        
                        val maxCount = buckets.maxOrNull()?.coerceAtLeast(1) ?: 1
                        val barWidth = width / bucketCount
                        
                        for (i in 0 until bucketCount) {
                            if (buckets[i] > 0) {
                                val intensity = buckets[i].toFloat() / maxCount.toFloat()
                                val barHeight = height * 0.4f + (height * 0.6f * intensity)
                                val x = i * barWidth
                                val y = (height - barHeight) / 2
                                
                                drawRoundRect(
                                    color = primaryColor.copy(alpha = 0.3f + 0.5f * intensity),
                                    topLeft = Offset(x + barWidth * 0.1f, y),
                                    size = Size(barWidth * 0.8f, barHeight),
                                    cornerRadius = CornerRadius(barWidth * 0.4f, barWidth * 0.4f)
                                )
                            }
                        }
                    }
                }

                RangeSlider(
                    value = sliderPosition,
                    onValueChange = { range ->
                        sliderPosition = range
                        onTimeRangeChanged(range.start.toLong(), range.endInclusive.toLong())
                    },
                    valueRange = minTime.toFloat()..maxTime.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                        inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                        thumbColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
