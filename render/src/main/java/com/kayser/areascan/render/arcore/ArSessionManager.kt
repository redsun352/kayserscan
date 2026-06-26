package com.kayser.areascan.render.arcore

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException

/**
 * ARCore oturum yönetimi. Thuban'daki AreaScanActivity'de gördüğümüz
 * "ARCore aç/kapat" (area_scan_navigationview_arcore) butonu ve kamera
 * rotasyon kilidi / ölçek-yönü FAB'lerinin karşılığıdır.
 *
 * ARCore tüm cihazlarda bulunmadığı için bu sınıf, kullanılamaz durumda
 * sessizce devre dışı kalacak şekilde tasarlandı — uygulamanın OpenGL-only
 * modu her zaman çalışmaya devam etmeli.
 */
class ArSessionManager(private val context: Context) {

    var session: Session? = null
        private set

    enum class Availability {
        SUPPORTED_INSTALLED,
        SUPPORTED_NOT_INSTALLED,
        UNSUPPORTED,
        UNKNOWN_CHECKING
    }

    fun checkAvailability(): Availability {
        return when (ArCoreApk.getInstance().checkAvailability(context)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> Availability.SUPPORTED_INSTALLED
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> Availability.SUPPORTED_NOT_INSTALLED
            ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> Availability.UNSUPPORTED
            else -> Availability.UNKNOWN_CHECKING
        }
    }

    /** ARCore APK kurulu değilse kullanıcıyı Play Store'a yönlendiren kurulum akışını tetikler. */
    fun requestInstall(activity: Activity, userRequestedInstall: Boolean): Boolean {
        return try {
            val installStatus = ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)
            installStatus == ArCoreApk.InstallStatus.INSTALLED
        } catch (e: UnavailableException) {
            false
        }
    }

    /** AR oturumunu başlatır. Sadece [checkAvailability] SUPPORTED_INSTALLED döndürdüğünde çağrılmalı. */
    fun createSession(): Session? {
        return try {
            val newSession = Session(context)
            val config = Config(newSession).apply {
                focusMode = Config.FocusMode.AUTO
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            }
            newSession.configure(config)
            session = newSession
            newSession
        } catch (e: UnavailableException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    fun resume() {
        try {
            session?.resume()
        } catch (e: Exception) {
            // Kamera başka bir uygulama tarafından kullanılıyor olabilir; sessizce yoksay,
            // UI tarafı state akışından hata durumunu görüp kullanıcıyı bilgilendirmeli.
        }
    }

    fun pause() {
        session?.pause()
    }

    fun close() {
        session?.close()
        session = null
    }
}
