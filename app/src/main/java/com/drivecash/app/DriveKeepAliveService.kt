package com.drivecash.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log

/**
 * 앱 화면이 내비게이션 등에 가려져도 주행 기록이 끊기지 않도록 프로세스를 살려 두는 포그라운드 서비스.
 *
 * 배경: 화면에서 가려진 앱 프로세스는 시스템이 "캐시" 상태로 보고 약 10초 뒤 얼린다(cached apps freezer).
 * 얼면 속도 수신과 거리 계산이 멈춰 내비를 켜고 운전하는 동안 적립이 중단된다.
 * (docs/DEVELOPMENT.md V-16, 2026-11-10 에뮬레이터 실측: 가린 뒤 거리 0.265km에서 정지)
 *
 * 이 서비스는 계산을 하지 않는다. 계산은 MainViewModel 이 하고, 서비스는 프로세스가 얼지 않게 하는 역할만 한다.
 * 포그라운드 서비스는 앱이 화면에 보일 때 시작해야 한다(Android 12 이후 백그라운드 시작 제한). 그래서 앱 실행 시 시작한다.
 */
class DriveKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "주행 기록", NotificationManager.IMPORTANCE_MIN).apply {
                description = "주행 중 거리와 안전운전 점수를 기록합니다"
                setShowBadge(false)
            },
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_drive)
            .setContentTitle("드라이브캐시")
            .setContentText("주행 기록 중")
            .setOngoing(true)
            .build()
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            Log.i(TAG, "[KEEPALIVE] 포그라운드 서비스 시작")
        } catch (t: Throwable) {
            Log.w(TAG, "[KEEPALIVE] 시작 실패: $t")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DriveCash"
        private const val CHANNEL_ID = "drive_record"
        private const val NOTIFICATION_ID = 1
    }
}
