package com.drivecash.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivecash.app.data.RewardItem
import com.drivecash.app.data.RewardStore
import com.drivecash.app.data.Voucher
import com.drivecash.app.domain.Mode
import com.drivecash.app.domain.NearbyPlace
import com.drivecash.app.domain.Places
import com.drivecash.app.domain.RewardPolicy
import com.drivecash.app.domain.TripReport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Accent = Color(0xFF00E676)
private val Warn = Color(0xFFFFAB00)
private val Danger = Color(0xFFFF5252)
private val CardBg = Color(0xFF14181C)
private val Muted = Color(0xFF9AA5A8)

private const val TARGET_WIDTH_DP = 1100f

private val DarkColors = darkColorScheme(
    primary = Accent,
    background = Color(0xFF050607),
    surface = CardBg,
    onSurface = Color(0xFFE8EBE4),
    onBackground = Color(0xFFE8EBE4),
)

@Composable
fun DriveCashApp(vm: MainViewModel, onOpenDev: () -> Unit) {
    val state by vm.ui.collectAsStateWithLifecycle()

    // Pleos Connect 에뮬레이터는 160dpi 라서 앱 창(약 1682x1210px)의 dp 폭이 1600을 넘는다.
    // 폭이 약 1100dp 가 되도록 밀도를 키워(최대 2배) 일반적인 크기의 UI 로 그린다.
    val density = LocalDensity.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val scale = (widthDp / TARGET_WIDTH_DP).coerceIn(1f, 2f)
    val scaled = Density(density.density * scale, density.fontScale)

    CompositionLocalProvider(LocalDensity provides scaled) {
        androidx.compose.material3.MaterialTheme(colorScheme = DarkColors) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (state.mode == Mode.DRIVING) {
                    DriveScreen(state)
                } else {
                    ParkScreen(state, vm, onOpenDev)
                }
                state.pendingReport?.let { ReportDialog(it, onDismiss = vm::dismissReport) }
                state.newVoucher?.let { VoucherDialog(it, onDismiss = vm::dismissVoucher) }
            }
        }
    }
}

// ============================================================================ 주행 모드

/** 주행 모드: 조작 버튼 없이 거리와 포인트만 크게 보여준다. (Safety-First UI) */
@Composable
private fun DriveScreen(state: UiState) {
    val live = state.live
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("주행 거리", color = Muted, fontSize = 22.sp)
            Text(
                "%.2f km".format(live?.distanceKm ?: 0.0),
                fontSize = 96.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            Text("+${live?.points ?: 0} P", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = Accent)
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val score = live?.safetyScore ?: 100
            Metric("안전운전", "$score", if (score >= RewardPolicy.SAFE_SCORE_THRESHOLD) Accent else Warn)
            Metric("속도", "${state.speedKmh} km/h", Color.White)
            Metric("제한속도", live?.speedLimitKmh?.let { "$it" } ?: "-", Muted)
        }

        state.toast?.let {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp),
                shape = RoundedCornerShape(16.dp),
                color = Accent,
            ) {
                Text(
                    it, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                    color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, fontSize = 16.sp)
        Text(value, color = color, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

// ============================================================================ 정차 모드

@Composable
private fun ParkScreen(state: UiState, vm: MainViewModel, onOpenDev: () -> Unit) {
    val rewards = state.rewards
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("현대 드라이브캐시", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Accent)
            Spacer(Modifier.weight(1f))
            Text(
                "정차 중 · 기어 ${state.gear}",
                color = Muted, fontSize = 16.sp,
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ---- 왼쪽: 포인트 / 출석 / 미션
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CardBox {
                    Text("보유 포인트", color = Muted, fontSize = 16.sp)
                    Text(
                        "${RewardStore.formatPoints(rewards.balance)} P",
                        fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Accent,
                    )
                }
                CardBox {
                    Text("출석체크", color = Muted, fontSize = 16.sp)
                    Text(
                        if (rewards.streakDays > 0) "연속 ${rewards.streakDays}일" else "아직 출석 기록이 없습니다",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = vm::attend,
                        enabled = !rewards.attendedToday,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (rewards.attendedToday) "오늘 출석 완료" else "출석하고 포인트 받기", fontSize = 18.sp)
                    }
                }
                CardBox {
                    Text("오늘의 미션", color = Muted, fontSize = 16.sp)
                    Text(
                        "${"%.0f".format(RewardPolicy.MISSION_KM)}km 이상 주행하며 안전운전 ${RewardPolicy.SAFE_SCORE_THRESHOLD}점 이상",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (rewards.todayKm / RewardPolicy.MISSION_KM).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (rewards.missionDoneToday) "달성 완료 (+${RewardPolicy.MISSION_POINTS}P)"
                        else "오늘 ${"%.2f".format(rewards.todayKm)}km / 보상 ${RewardPolicy.MISSION_POINTS}P",
                        color = if (rewards.missionDoneToday) Accent else Muted, fontSize = 16.sp,
                    )
                }
                NearbyCard(state.nearby)
                state.notice?.let { Text(it, color = Warn, fontSize = 18.sp) }
            }

            // ---- 가운데: 최근 주행
            Column(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
                Text("최근 주행", color = Muted, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                if (rewards.trips.isEmpty()) {
                    Text("주행 기록이 없습니다. 기어를 D로 바꿔 주행을 시작하세요.", color = Muted, fontSize = 16.sp)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rewards.trips) { TripRow(it) }
                }
            }

            // ---- 오른쪽: 포인트 사용
            Column(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
                Text("포인트 사용 (시연용)", color = Muted, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(vm.catalog) { item ->
                        RewardRow(item, canAfford = rewards.balance >= item.cost, onRedeem = { vm.redeem(item) })
                    }
                    if (rewards.vouchers.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text("교환 내역", color = Muted, fontSize = 16.sp)
                        }
                        items(rewards.vouchers.take(3)) { v ->
                            Text("${v.title}  ·  ${v.code}", fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("개발자 검증 화면", color = Muted, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onOpenDev))
            Text("시연용 포인트 +10,000P", color = Muted, fontSize = 14.sp, modifier = Modifier.clickable(onClick = vm::grantDemoPoints))
            Text("시연 데이터 초기화", color = Muted, fontSize = 14.sp, modifier = Modifier.clickable(onClick = vm::resetDemo))
            Text(state.status, color = Muted, fontSize = 12.sp)
        }
    }
}

/** 현재 위치에서 가까운 사용처. 장소 데이터는 시연용 예시다. (domain/Places.kt) */
@Composable
private fun NearbyCard(nearby: List<NearbyPlace>?) {
    CardBox {
        Text("가까운 사용처 (시연용 예시)", color = Muted, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        if (nearby == null) {
            Text("현재 위치 확인 중", color = Muted, fontSize = 16.sp)
        } else {
            nearby.forEach {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${it.place.kind.label}  ${it.place.name}", fontSize = 16.sp)
                    Text(Places.formatDistance(it.distanceMeters), color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = CardBg, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun TripRow(t: TripReport) {
    val fmt = remember { DateTimeFormatter.ofPattern("M/d HH:mm") }
    val whenText = Instant.ofEpochMilli(t.startMs).atZone(ZoneId.systemDefault()).format(fmt)
    Surface(shape = RoundedCornerShape(12.dp), color = CardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(whenText, color = Muted, fontSize = 14.sp)
                Text("%.2f km".format(t.distanceKm), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("안전 ${t.safetyScore}", color = scoreColor(t.safetyScore), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("+${t.totalPoints} P", color = Accent, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun RewardRow(item: RewardItem, canAfford: Boolean, onRedeem: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = CardBg, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(item.description, color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onRedeem,
                enabled = canAfford,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
            ) { Text("${RewardStore.formatPoints(item.cost)}P", fontSize = 16.sp) }
        }
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= RewardPolicy.SAFE_SCORE_THRESHOLD -> Accent
    score >= 70 -> Warn
    else -> Danger
}

// ============================================================================ 대화상자

@Composable
private fun ReportDialog(r: TripReport, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // 기본 폭 제한을 풀어 창을 넓힌다. 영상과 큰 화면에서 잘 읽히도록 글씨도 키웠다.
        modifier = Modifier.widthIn(min = 720.dp, max = 820.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인", fontSize = 30.sp, color = Accent, fontWeight = FontWeight.Bold) } },
        title = { Text("주행 리포트", fontSize = 40.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportLine("총 주행거리", "%.2f km".format(r.distanceKm))
                ReportLine("평균 / 최고 속도", "%.0f / %.0f km/h".format(r.avgSpeedKmh, r.maxSpeedKmh))
                ReportLine("안전운전 점수", "${r.safetyScore}점", scoreColor(r.safetyScore))
                ReportLine("급가속 / 급제동 / 과속", "${r.harshAccel}회 / ${r.harshBrake}회 / ${r.speedingSec}초")
                Spacer(Modifier.height(14.dp))
                ReportLine("거리 포인트", "+${r.distancePoints}")
                if (r.spotBonusPoints > 0) ReportLine("스팟 보너스", "+${r.spotBonusPoints}")
                if (r.zoneBonusPoints > 0) ReportLine("구간 진입 보너스", "+${r.zoneBonusPoints}")
                if (r.safeBonusPoints > 0) ReportLine("안전운전 보너스", "+${r.safeBonusPoints}")
                if (r.missionBonusPoints > 0) ReportLine("데일리 미션", "+${r.missionBonusPoints}")
                Spacer(Modifier.height(6.dp))
                ReportLine("총 적립", "+${r.totalPoints} P", Accent, big = true)
            }
        },
    )
}

@Composable
private fun ReportLine(label: String, value: String, color: Color = Color.Unspecified, big: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = if (big) 28.sp else 26.sp)
        Spacer(Modifier.width(32.dp))
        Text(value, color = color, fontSize = if (big) 40.sp else 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VoucherDialog(v: Voucher, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 760.dp, max = 860.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기", fontSize = 30.sp, color = Accent, fontWeight = FontWeight.Bold) } },
        title = { Text(v.title, fontSize = 38.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Barcode(v.code)
                Spacer(Modifier.height(18.dp))
                Text(v.code, fontSize = 46.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("시연용 코드입니다. 실제 사용처와 연동되지 않습니다.", color = Muted, fontSize = 22.sp, textAlign = TextAlign.Center)
            }
        },
    )
}

/** 코드 문자열에서 막대 패턴을 만들어 그리는 시연용 바코드. 실제 바코드 규격이 아니다. */
@Composable
private fun Barcode(code: String) {
    val bits = code.flatMap { ch ->
        (0 until 7).map { i -> ((ch.code shr i) and 1) == 1 }
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        // 막대(굵기 1.0 또는 1.6)와 간격(0.6)을 모두 합친 전체 너비가 캔버스 폭과 같아지도록 단위 길이를 구한다.
        val gap = 0.6f
        val totalUnits = bits.sumOf { (if (it) 1.6 else 1.0) + gap }.toFloat() - gap
        val unit = size.width / totalUnits
        var x = 0f
        bits.forEach { thick ->
            val w = (if (thick) 1.6f else 1.0f) * unit
            drawRect(Color.White, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w + gap * unit
        }
    }
}
