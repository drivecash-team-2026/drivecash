package com.drivecash.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivecash.app.ui.DriveCashApp
import com.drivecash.app.ui.MainViewModel
import com.drivecash.app.verify.SdkVerifier

/**
 * 기본 화면은 드라이브캐시 앱이다. 개발자 검증 화면(SdkVerifier)으로 전환할 수 있다.
 * `adb shell am start -n com.drivecash.app/.MainActivity --ez autorun true` 는 검증 화면을 바로 열고 자동 실행한다.
 */
class MainActivity : ComponentActivity() {

    private val appViewModel: MainViewModel by viewModels()

    private var verifier: SdkVerifier? = null
    private val lines = mutableStateListOf<String>()
    private var showDev by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 아래 퍼미션은 런타임 허용이 필요하다. (adb install -g 로 미리 허용 가능)
        // - FusedLocation: 위치, 스팟 구간 근접 알림
        // - CAR_SPEED: AAOS 차량 속도. 새로 설치하면 granted=false 이고 허용하기 전에는 속도를 읽지 못한다. (2026-11-10 실측)
        // 화면이 가려져도 프로세스가 얼지 않도록 포그라운드 서비스를 시작한다. 앱이 보이는 지금 시작해야 허용된다.
        runCatching { startForegroundService(android.content.Intent(this, DriveKeepAliveService::class.java)) }
            .onFailure { android.util.Log.w("DriveCash", "[KEEPALIVE] 서비스 시작 요청 실패: $it") }

        val missing = REQUIRED_RUNTIME_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)

        val autorun = intent.getBooleanExtra("autorun", false)
        showDev = autorun

        setContent {
            if (showDev) {
                DevScreen()
            } else {
                DriveCashApp(appViewModel, onOpenDev = { showDev = true })
            }
        }

        if (autorun) startVerification()
    }

    @androidx.compose.runtime.Composable
    private fun DevScreen() {
        MaterialTheme {
            val listState = rememberLazyListState()
            LaunchedEffect(lines.size) {
                if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("DriveCash SDK 검증", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { startVerification() }) { Text("전체 실행") }
                    Button(onClick = { lines.clear() }) { Text("지우기") }
                    Button(onClick = { stopVerification(); showDev = false }) { Text("앱으로 돌아가기") }
                }
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(lines) { Text(it, fontSize = 13.sp) }
                }
            }
        }
    }

    private fun startVerification() {
        verifier?.release()
        // --ez route true 이면 내비 경로 안내까지 시험한다. --ei listen 60 으로 관찰 시간(초)을 늘릴 수 있다.
        val route = intent.getBooleanExtra("route", false)
        val listen = intent.getIntExtra("listen", 12)
        verifier = SdkVerifier(applicationContext) { lines.add(it) }.also { it.runAll(listen, route) }
    }

    private fun stopVerification() {
        verifier?.release()
        verifier = null
    }

    override fun onDestroy() {
        stopVerification()
        super.onDestroy()
    }

    private companion object {
        val REQUIRED_RUNTIME_PERMISSIONS = listOf(
            SdkVerifier.PERM_FUSED_LOCATION,
            "android.car.permission.CAR_SPEED",
        )
    }
}
