package org.example.project

import androidx.compose.runtime.Composable
import com.redcakefinance.ui.AppScreen
import com.redcakefinance.ui.theme.AppTheme

/**
 * iOS / Android 共用的 Compose 入口。
 *
 * 平台入口（`MainViewController.kt`、`androidApp` 的 `MainActivity`）只负责挂载这一层，
 * 具体界面与业务都在 `com.redcakefinance` 下。
 */
@Composable
fun App() {
    AppTheme { AppScreen() }
}
