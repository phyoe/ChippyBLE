// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false // Use alias from TOML
    alias(libs.plugins.kotlin.android) apply false      // Use alias from TOML
}