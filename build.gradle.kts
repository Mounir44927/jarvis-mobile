// Jarvis Mobile — root build file.
// Plugins are declared once here and applied in :app only.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
