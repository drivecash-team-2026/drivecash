package com.drivecash.app.data.signals

import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationTargetException

/**
 * AAOS 표준 차량 속성(CarPropertyManager)을 리플렉션으로 읽는다.
 *
 * `android.car` 는 공개 SDK에 없어서 직접 참조할 수 없다. Pleos Vehicle SDK에는 차량 속도 API가 없어
 * AAOS 표준 `PERF_VEHICLE_SPEED` 를 쓴다. (docs/DEVELOPMENT.md V-02, D-017)
 *
 * 필요한 선언: `<uses-library android:name="android.car" android:required="false"/>`,
 * `android.car.permission.CAR_SPEED`, `android.car.permission.CAR_POWERTRAIN`.
 * 미확인: Pleos App Market 심사에서 `android.car.permission.*` 선언이 허용되는지.
 */
class AaosCar(private val context: Context) {

    private var car: Any? = null
    private var carClass: Class<*>? = null
    private var manager: Any? = null
    private var reportedUnavailable = false

    fun connect() {
        if (car != null) return
        try {
            val cls = Class.forName("android.car.Car")
            carClass = cls
            car = cls.getMethod("createCar", Context::class.java).invoke(null, context)
            Log.i(TAG, "[AAOS] Car.createCar ${if (car != null) "성공" else "null"}")
        } catch (t: Throwable) {
            Log.w(TAG, "[AAOS] 사용 불가: ${unwrap(t)}")
        }
    }

    /** 속도(m/s). 읽지 못하면 null. */
    fun speedMps(): Float? = readFloat(PERF_VEHICLE_SPEED)

    /** AAOS GEAR_SELECTION 원시값. 읽지 못하면 null. (4=PARK, 8=DRIVE, 2=REVERSE, 1=NEUTRAL) */
    fun gearRaw(): Int? = readInt(GEAR_SELECTION)

    fun isReady(): Boolean = propertyManager() != null

    private fun propertyManager(): Any? {
        manager?.let { return it }
        val c = car ?: return null
        val cls = carClass ?: return null
        return try {
            val connected = cls.getMethod("isConnected").invoke(c) as? Boolean ?: false
            if (!connected) return null
            manager = cls.getMethod("getCarManager", String::class.java).invoke(c, "property")
            manager
        } catch (t: Throwable) {
            if (!reportedUnavailable) Log.w(TAG, "[AAOS] CarPropertyManager 획득 실패: ${unwrap(t)}")
            reportedUnavailable = true
            null
        }
    }

    private fun readFloat(propId: Int): Float? {
        val m = propertyManager() ?: return null
        return try {
            m.javaClass.getMethod("getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(m, propId, 0) as? Float
        } catch (t: Throwable) {
            logOnce("float 0x${propId.toString(16)}", t)
            null
        }
    }

    private fun readInt(propId: Int): Int? {
        val m = propertyManager() ?: return null
        return try {
            m.javaClass.getMethod("getIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(m, propId, 0) as? Int
        } catch (t: Throwable) {
            logOnce("int 0x${propId.toString(16)}", t)
            null
        }
    }

    private val logged = mutableSetOf<String>()
    private fun logOnce(key: String, t: Throwable) {
        if (logged.add(key)) Log.w(TAG, "[AAOS] 읽기 실패 $key: ${unwrap(t)}")
    }

    private fun unwrap(t: Throwable): Throwable = (t as? InvocationTargetException)?.targetException ?: t

    companion object {
        private const val TAG = "DriveCash"
        /** VehiclePropertyIds.PERF_VEHICLE_SPEED */
        const val PERF_VEHICLE_SPEED = 0x11600207
        /** VehiclePropertyIds.GEAR_SELECTION */
        const val GEAR_SELECTION = 0x11400400
    }
}
