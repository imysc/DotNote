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
    // 0 나누기 방지 및 정밀도 유지를 위해 최소 1초의 시간 간격 확보
    val timeRange = remember(minTime, maxTime) { (maxTime - minTime).coerceAtLeast(1000L) }
    
    // minTime, maxTime 갱신 시 슬라이더 상태를 0f..1f로 재설정하고 remember 키로 동기화
    var sliderPosition by remember(minTime, maxTime) { mutableStateOf(0f..1f) }
    val formatter = remember { SimpleDateFormat("MM.dd", Locale.getDefault()) }

    // 슬라이더 비율 값으로부터 실제 타임스탬프 역산
    val startTimestamp = remember(sliderPosition.start, minTime, timeRange) {
        minTime + (sliderPosition.start * timeRange).toLong()
    }
    val endTimestamp = remember(sliderPosition.endInclusive, minTime, timeRange) {
        minTime + (sliderPosition.endInclusive * timeRange).toLong()
    }

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
                    text = formatter.format(Date(startTimestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatter.format(Date(endTimestamp)),
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
                        val bucketCount = 40
                        val buckets = IntArray(bucketCount)
                        
                        memos.forEach { memo ->
                            val t = memo.createdAt.coerceIn(minTime, maxTime)
                            val ratio = (t - minTime).toFloat() / timeRange.toFloat()
                            val idx = (ratio * (bucketCount - 1)).toInt().coerceIn(0, bucketCount - 1)
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
                        val start = minTime + (range.start * timeRange).toLong()
                        val end = minTime + (range.endInclusive * timeRange).toLong()
                        onTimeRangeChanged(start, end)
                    },
                    valueRange = 0f..1f,
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
