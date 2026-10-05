// 顶层构建文件：只声明插件，具体配置放在 app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 内置 Kotlin，不需要 org.jetbrains.kotlin.android
    alias(libs.plugins.kotlin.compose) apply false
}
