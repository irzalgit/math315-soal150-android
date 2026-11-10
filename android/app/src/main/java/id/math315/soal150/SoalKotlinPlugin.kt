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
            val intent = Intent(activity, SoalKotlinActivity::class.java)
            activity.startActivity(intent)
            call.resolve()
        } catch (e: Exception) {
            call.reject("Gagal membuka soal Kotlin: ${e.message}")
        }
    }
}
