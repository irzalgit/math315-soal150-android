package id.math315.soal150

import android.content.Intent
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "SoalKotlin")
class SoalKotlinPlugin : Plugin() {

    @PluginMethod
    fun open(call: PluginCall) {
        try {
            val uid = call.getString("uid") ?: ""
            val isPremium = call.getBoolean("isPremium") ?: false
            val premiumUntil = call.getLong("premiumUntil", 0L)

            val intent = Intent(activity, SoalKotlinActivity::class.java).apply {
                putExtra("uid", uid)
                putExtra("isPremium", isPremium)
                putExtra("premiumUntil", premiumUntil)
            }

            activity.startActivity(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Gagal membuka soal Kotlin: ${e.message}")
        }
    }
}
