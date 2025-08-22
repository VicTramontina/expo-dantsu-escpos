package expo.modules.dantsuescpos
import android.annotation.SuppressLint
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoDantsuEscposModule : Module() {
    @SuppressLint("MissingPermission")
    override fun definition() = ModuleDefinition {
        Name("ExpoDantsuEscposModule")
    }
}
