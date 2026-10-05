package com.example.danmaku

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 弹幕舞台：[trackCount] 条轨道上下平分整个舞台，每条轨道上都有一条 [text] 在匀速飘，
 * 飘完停 [gapMillis] 毫秒再来一轮，无限循环。多条轨道之间会把起跑时间错开，
 * 避免所有弹幕并排同时出现看着像一条。
 *
 * 位置计算全在像素空间里做：
 *   向右飘：起点 x = -文字宽度，终点 x = 舞台宽度
 *   向左飘：起点 x = 舞台宽度，终点 x = -文字宽度
 * 动画时长 = 总路程 / 速度，用 LinearEasing 保证匀速。
 *
 * @param speedDpPerSec 每秒移动多少 dp（越大越快）
 * @param leftToRight   true = 从左往右飘；false = 从右往左飘（B 站横屏弹幕方向）
 * @param trackCount    同时飘几条（1~5），每条占一条横向轨道
 * @param strokeEnabled 是否给文字描边（描边画在文字底下，字压在描边上）
 * @param strokeWidthDp 描边粗细（dp）
 * @param strokeColor   描边颜色
 */
@Composable
fun DanmakuStage(
    text: String,
    fontSp: Float,
    color: Color,
    speedDpPerSec: Float,
    leftToRight: Boolean,
    playing: Boolean,
    trackCount: Int,
    strokeEnabled: Boolean,
    strokeWidthDp: Float,
    strokeColor: Color,
    gapMillis: Long = 500L,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()

    // 用 rememberUpdatedState 读速度：拖速度条时不会打断正在飞的那一轮，下一轮按新速度走
    val currentSpeed = rememberUpdatedState(speedDpPerSec)

    var stageWidthPx by remember { mutableIntStateOf(0) }

    // 预算文字宽度（像素）：动画要用它算出屏幕外的起点/终点
    val textWidthPx = remember(text, fontSp) {
        if (text.isEmpty()) {
            0
        } else {
            measurer.measure(
                text = AnnotatedString(text),
                style = TextStyle(fontSize = fontSp.sp, fontWeight = FontWeight.SemiBold)
            ).size.width
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { stageWidthPx = it.width },
        contentAlignment = Alignment.Center
    ) {
        if (text.isEmpty()) {
            Text(
                text = "在下方输入文字，点「播放」开始滚动",
                fontSize = 16.sp,
                color = Color(0xFF9AA0AC)
            )
        } else {
            val tracks = trackCount.coerceIn(1, MAX_TRACKS)
            Column(modifier = Modifier.fillMaxSize()) {
                repeat(tracks) { index ->
                    DanmakuTrack(
                        text = text,
                        fontSp = fontSp,
                        color = color,
                        strokeEnabled = strokeEnabled,
                        strokeWidthDp = strokeWidthDp,
                        strokeColor = strokeColor,
                        speed = currentSpeed,
                        leftToRight = leftToRight,
                        playing = playing,
                        stageWidthPx = stageWidthPx,
                        textWidthPx = textWidthPx,
                        gapMillis = gapMillis,
                        trackIndex = index,
                        trackCount = tracks,
                        // 每条轨道平分舞台高度；故意不 clip，字大了就压到隔壁轨道上，
                        // 跟真弹幕一样：宁可重叠，也不要被切掉一半
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

/** 舞台最多支持几条轨道（界面上滑杆的上限也是它） */
const val MAX_TRACKS = 5

/**
 * 一条弹幕轨道：自己一个 [Animatable] 管横向位置，循环跑「飘一趟 → 停一下 → 再飘」。
 */
@Composable
private fun DanmakuTrack(
    text: String,
    fontSp: Float,
    color: Color,
    strokeEnabled: Boolean,
    strokeWidthDp: Float,
    strokeColor: Color,
    speed: State<Float>,
    leftToRight: Boolean,
    playing: Boolean,
    stageWidthPx: Int,
    textWidthPx: Int,
    gapMillis: Long,
    trackIndex: Int,
    trackCount: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(playing, text, fontSp, leftToRight, stageWidthPx, textWidthPx, trackCount, trackIndex) {
        // 文字为空、还没量到宽度、或者暂停状态：什么都不做（文字停在原地）
        if (!playing || textWidthPx <= 0 || stageWidthPx <= 0) return@LaunchedEffect

        val startX = if (leftToRight) -textWidthPx.toFloat() else stageWidthPx.toFloat()
        val endX = if (leftToRight) stageWidthPx.toFloat() else -textWidthPx.toFloat()
        val distancePx = abs(endX - startX)

        // 每次都用「当前」速度算时长，所以拖速度条不影响正在飞的那一轮
        fun durationMsNow(): Int {
            val speedPxPerSec = with(density) { speed.value.dp.toPx() }.coerceAtLeast(1f)
            return (distancePx / speedPxPerSec * 1000f).roundToInt().coerceAtLeast(1)
        }

        offsetX.snapTo(startX)

        // 多条轨道时把起跑时间错开（只错开第一次，之后各跑各的周期就不会越跑越乱）
        if (trackCount > 1) {
            delay((durationMsNow() + gapMillis) * trackIndex / trackCount)
        }

        while (isActive) {
            offsetX.snapTo(startX)
            offsetX.animateTo(
                targetValue = endX,
                animationSpec = tween(durationMsNow(), easing = LinearEasing)
            )
            delay(gapMillis)
        }
    }

    val strokeWidthPx = with(density) { strokeWidthDp.dp.toPx() }
    val showStroke = strokeEnabled && strokeWidthPx > 0f

    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        // 先画描边（粗一圈铺在底下），再把实心文字压上去 —— 这就是「描边字」
        if (showStroke) {
            Text(
                text = text,
                style = TextStyle(
                    fontSize = fontSp.sp,
                    fontWeight = FontWeight.SemiBold,
                    drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round)
                ),
                color = strokeColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
            )
        }
        Text(
            text = text,
            style = TextStyle(fontSize = fontSp.sp, fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
        )
    }
}
