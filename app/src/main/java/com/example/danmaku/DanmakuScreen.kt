package com.example.danmaku

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.danmaku.ui.theme.DanmakuTheme
import kotlin.math.roundToInt

/** 预设的弹幕颜色（都是白底上看得清的深色） */
private val PresetColors = listOf(
    0xFF111111L, // 近黑
    0xFFE53935L, // 红
    0xFFFB8C00L, // 橙
    0xFFF9A825L, // 黄
    0xFF2E7D32L, // 绿
    0xFF00897BL, // 青
    0xFF1E88E5L, // 蓝
    0xFF8E24AAL, // 紫
    0xFFD81B60L, // 粉
    0xFF6D4C41L  // 棕
)

/** 描边颜色选项：0 = 自动（按字色明暗挑对比色），1 = 黑，2 = 白 */
private const val STROKE_COLOR_AUTO = 0
private const val STROKE_COLOR_BLACK = 1
private const val STROKE_COLOR_WHITE = 2

/** 一行一个调节组，宽度固定 190dp —— 三行都靠它对齐 */
private val GROUP_WIDTH = 190.dp

/**
 * 这个颜色「看上去亮不亮」。
 *
 * 注意不能用 [androidx.compose.ui.graphics.luminance]：那是 WCAG 相对亮度，橙色 `#F9A825`
 * 只有 0.48，会被判成深色，于是「自动描边」给浅色字配了个白边 —— 白底上等于没描边。
 * 这里用 YIQ 感知亮度（橙黄 ≈ 0.70），跟人眼判断一致。
 */
private fun Color.isLight(): Boolean =
    0.299f * red + 0.587f * green + 0.114f * blue > 0.6f

/**
 * 主界面：上面是弹幕舞台，下面是居中的控制面板，整体纯白底。
 */
@Composable
fun DanmakuScreen(modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("你好，弹幕！") }
    var speed by rememberSaveable { mutableStateOf(320f) }          // dp / 秒
    var fontSp by rememberSaveable { mutableStateOf(36f) }          // sp
    var colorArgb by rememberSaveable { mutableStateOf(0xFF111111L) }
    var leftToRight by rememberSaveable { mutableStateOf(false) }   // 默认 B 站方向
    var playing by rememberSaveable { mutableStateOf(true) }
    var trackCount by rememberSaveable { mutableIntStateOf(3) }     // 同时飘几条
    var strokeEnabled by rememberSaveable { mutableStateOf(false) } // 文字描边开关
    var strokeWidthDp by rememberSaveable { mutableStateOf(2f) }    // 描边粗细
    var strokeColorMode by rememberSaveable { mutableIntStateOf(STROKE_COLOR_AUTO) }
    // 控制面板是否显示：点舞台空白处可以收起，再点一下回来
    var settingsVisible by rememberSaveable { mutableStateOf(true) }

    val focusManager = LocalFocusManager.current

    val textColor = Color(colorArgb.toInt())
    val strokeColor = when (strokeColorMode) {
        STROKE_COLOR_BLACK -> Color.Black
        STROKE_COLOR_WHITE -> Color.White
        // 自动：浅色字配黑边，深色字配白边
        else -> if (textColor.isLight()) Color.Black else Color.White
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 点舞台空白处：收起软键盘 + 收起/展开下面的控制面板。
            // 收起后舞台占满整屏，弹幕改成按整屏高度居中分布。
            DanmakuStage(
                text = text,
                fontSp = fontSp,
                color = textColor,
                speedDpPerSec = speed,
                leftToRight = leftToRight,
                playing = playing,
                trackCount = trackCount,
                strokeEnabled = strokeEnabled,
                strokeWidthDp = strokeWidthDp,
                strokeColor = strokeColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            focusManager.clearFocus()
                            settingsVisible = !settingsVisible
                        }
                    }
            )

            AnimatedVisibility(
                visible = settingsVisible,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(color = Color(0x14000000))

                    ControlPanel(
                        text = text,
                        onTextChange = { text = it },
                        playing = playing,
                        onTogglePlaying = { playing = !playing },
                        speed = speed,
                        onSpeedChange = { speed = it },
                        fontSp = fontSp,
                        onFontSpChange = { fontSp = it },
                        colorArgb = colorArgb,
                        onColorChange = { colorArgb = it },
                        leftToRight = leftToRight,
                        onDirectionChange = { leftToRight = it },
                        trackCount = trackCount,
                        onTrackCountChange = { trackCount = it },
                        strokeEnabled = strokeEnabled,
                        onStrokeEnabledChange = { strokeEnabled = it },
                        strokeWidthDp = strokeWidthDp,
                        onStrokeWidthChange = { strokeWidthDp = it },
                        strokeColorMode = strokeColorMode,
                        onStrokeColorModeChange = { strokeColorMode = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlPanel(
    text: String,
    onTextChange: (String) -> Unit,
    playing: Boolean,
    onTogglePlaying: () -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    fontSp: Float,
    onFontSpChange: (Float) -> Unit,
    colorArgb: Long,
    onColorChange: (Long) -> Unit,
    leftToRight: Boolean,
    onDirectionChange: (Boolean) -> Unit,
    trackCount: Int,
    onTrackCountChange: (Int) -> Unit,
    strokeEnabled: Boolean,
    onStrokeEnabledChange: (Boolean) -> Unit,
    strokeWidthDp: Float,
    onStrokeWidthChange: (Float) -> Unit,
    strokeColorMode: Int,
    onStrokeColorModeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAbout by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 第一行：输入文字 + 播放/暂停
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                singleLine = true,
                placeholder = { Text("在这里输入弹幕文字") },
                modifier = Modifier.width(320.dp)
            )
            Button(onClick = onTogglePlaying) {
                Text(if (playing) "暂停" else "播放")
            }
            OutlinedButton(onClick = { showAbout = true }) {
                Text("关于")
            }
        }

        // 第二行：速度 / 字号 / 颜色 / 方向
        AdaptiveControlRow {
            CompactSlider(
                label = "速度",
                valueText = "${speed.roundToInt()} dp/秒",
                value = speed,
                min = 60f,
                max = 1600f,
                onValueChange = onSpeedChange
            )
            CompactSlider(
                label = "字号",
                valueText = "${fontSp.roundToInt()} sp",
                value = fontSp,
                min = 14f,
                max = 140f,
                onValueChange = onFontSpChange
            )
            ColorRow(colorArgb = colorArgb, onColorChange = onColorChange)
            DirectionRow(leftToRight = leftToRight, onDirectionChange = onDirectionChange)
        }

        // 第三行：轨道（同时飘几条）/ 描边开关+颜色 / 描边粗细
        AdaptiveControlRow {
            CompactSlider(
                label = "轨道",
                valueText = "$trackCount 条",
                value = trackCount.toFloat(),
                min = 1f,
                max = MAX_TRACKS.toFloat(),
                steps = MAX_TRACKS - 2,
                onValueChange = { onTrackCountChange(it.roundToInt()) }
            )
            StrokeRow(
                enabled = strokeEnabled,
                onEnabledChange = onStrokeEnabledChange,
                mode = strokeColorMode,
                onModeChange = onStrokeColorModeChange
            )
            CompactSlider(
                label = "粗细",
                valueText = "%.1f dp".format(strokeWidthDp),
                value = strokeWidthDp,
                min = 0.5f,
                max = 8f,
                enabled = strokeEnabled,
                onValueChange = onStrokeWidthChange
            )
        }
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

/**
 * 「关于」弹窗：制作人 + 版本号。
 *
 * 版本号是运行时从 PackageManager 读的，不是写死的字符串 —— 改 `app/build.gradle.kts` 里的
 * `versionName` 就自动跟着变。API 33 起 `getPackageInfo(String, Int)` 被弃用，所以要分支。
 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName
        }.getOrNull() ?: "1.0"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutRow("应用名称", "弹幕Demo")
                AboutRow("制作人", "sjy")
                AboutRow("版本号", versionName)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("好的") }
        }
    )
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF6B7280),
            modifier = Modifier.width(72.dp)
        )
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 放得下就整体居中，放不下就变成横向滚动（横屏手机窄，比硬挤换行好看）。
 */
@Composable
private fun AdaptiveControlRow(content: @Composable RowScope.() -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minWidth = maxWidth
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = minWidth),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun GroupLabel(text: String, enabled: Boolean = true) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = if (enabled) Color(0xFF44474E) else Color(0xFF9AA0AC)
    )
}

/**
 * 一行搞定的滑杆组：`名称 [滑杆] 数值`。这样三行控制面板只占 ~180dp 高，把地方留给弹幕舞台。
 */
@Composable
private fun CompactSlider(
    label: String,
    valueText: String,
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.width(GROUP_WIDTH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupLabel(label, enabled)
        Spacer(Modifier.width(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = valueText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            color = if (enabled) Color(0xFF111111) else Color(0xFF9AA0AC),
            modifier = Modifier.width(56.dp)
        )
    }
}

@Composable
private fun ColorRow(colorArgb: Long, onColorChange: (Long) -> Unit) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        GroupLabel("颜色")
        Spacer(Modifier.width(8.dp))
        // 10 个预设色排成「5 列 x 2 行」，横屏寸土寸金，比一字排开省一半宽度
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            PresetColors.chunked(5).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    line.forEach { preset ->
                        val selected = preset == colorArgb
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(preset))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) Color(0xFF1E6DF2) else Color(0x22000000),
                                    shape = CircleShape
                                )
                                .clickable { onColorChange(preset) }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        OutlinedButton(
            onClick = { showCustomDialog = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(30.dp)
        ) {
            Text("自定义", fontSize = 12.sp)
        }
    }

    if (showCustomDialog) {
        CustomColorDialog(
            initial = Color(colorArgb.toInt()),
            onDismiss = { showCustomDialog = false },
            onConfirm = { picked ->
                onColorChange(picked.toArgb().toLong() and 0xFFFFFFFFL)
                showCustomDialog = false
            }
        )
    }
}

/**
 * 描边选项：一个开关 + 描边颜色三选一。颜色选「自动」时就按字色明暗自动配对比色。
 * 关掉开关时颜色和粗细都会变灰、不可点。
 */
@Composable
private fun StrokeRow(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    mode: Int,
    onModeChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GroupLabel("描边", enabled)
        Spacer(Modifier.width(6.dp))
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
        Spacer(Modifier.width(6.dp))
        StrokeColorChip("自动", STROKE_COLOR_AUTO, mode, enabled, onModeChange)
        Spacer(Modifier.width(6.dp))
        StrokeColorChip("黑", STROKE_COLOR_BLACK, mode, enabled, onModeChange)
        Spacer(Modifier.width(6.dp))
        StrokeColorChip("白", STROKE_COLOR_WHITE, mode, enabled, onModeChange)
    }
}

@Composable
private fun StrokeColorChip(
    label: String,
    value: Int,
    mode: Int,
    enabled: Boolean,
    onModeChange: (Int) -> Unit
) {
    FilterChip(
        selected = mode == value,
        onClick = { onModeChange(value) },
        enabled = enabled,
        label = { Text(label, fontSize = 12.sp) }
    )
}

@Composable
private fun DirectionRow(leftToRight: Boolean, onDirectionChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GroupLabel("方向")
        Spacer(Modifier.width(6.dp))
        FilterChip(
            selected = !leftToRight,
            onClick = { onDirectionChange(false) },
            label = { Text("从右往左", fontSize = 12.sp) }
        )
        Spacer(Modifier.width(6.dp))
        FilterChip(
            selected = leftToRight,
            onClick = { onDirectionChange(true) },
            label = { Text("从左往右", fontSize = 12.sp) }
        )
    }
}

@Composable
private fun CustomColorDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    var red by remember { mutableStateOf(initial.red) }
    var green by remember { mutableStateOf(initial.green) }
    var blue by remember { mutableStateOf(initial.blue) }
    val picked = Color(red, green, blue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义颜色") },
        text = {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(picked)
                        .border(1.dp, Color(0x22000000), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "示例：弹幕飘过",
                        color = if (picked.isLight()) Color.Black else Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(10.dp))
                ChannelSlider("R", red) { red = it }
                ChannelSlider("G", green) { green = it }
                ChannelSlider("B", blue) { blue = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ChannelSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, modifier = Modifier.width(18.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = (value * 255f).roundToInt().toString(),
            fontSize = 13.sp,
            modifier = Modifier.width(40.dp)
        )
    }
}

@Preview(widthDp = 891, heightDp = 411)
@Composable
private fun DanmakuScreenPreview() {
    DanmakuTheme {
        DanmakuScreen()
    }
}
