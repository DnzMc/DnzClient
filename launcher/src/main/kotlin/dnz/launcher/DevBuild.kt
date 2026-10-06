package dnz.launcher

/** Developer-only features (the test account) show only in the developer launcher, never in a released build. */
object DevBuild {
    /** Set by "gradlew run" (build.gradle.kts); the installer, portable and Mac app never set it. */
    val fromSource: Boolean = System.getProperty("dnz.dev") == "true"
}
