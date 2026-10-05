package com.example.danmaku

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.danmaku.ui.theme.DanmakuTheme

/**
 * 唯一的一个 Activity。横屏由 AndroidManifest 里的 screenOrientation="landscape" 锁死。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 白底界面：强制状态栏/导航栏用深色图标，别在白色上看不见
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // 一直飘的时候别让屏幕自己灭掉
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            DanmakuTheme {
                DanmakuScreen()
            }
        }
    }
}
