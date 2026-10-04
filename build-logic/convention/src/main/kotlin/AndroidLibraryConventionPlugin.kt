import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidLibraryConventionPlugin : Plugin<Project>
{
    /**
     * apply(target) is the function Gradle calls. target is the module that applied your plugin.
     */
    override fun apply(target: Project) {
        /**
         * pluginManager.apply("com.android.library") makes that module an Android library, so the module no longer has to say so itself.
         */
        target.pluginManager.apply("com.android.library")

        /**
         * getByType(LibraryExtension::class.java) fetches the object behind the android { } block. The Android plugin created it one line earlier, which is why the order matters.
         */
        val android : LibraryExtension = target.extensions.getByType(LibraryExtension::class.java)
        configureAndroidCommon(android = android)
        // Robolectric-backed tests (Room, Compose) need real resources
        // (themes, strings) merged in, not just the stub SDK jar.
        android.testOptions.unitTests.isIncludeAndroidResources = true
        // Conscrypt, which Robolectric installs as a security provider,
        // lowercases os.name with the default locale. Under tr-TR
        // "Windows" becomes "wındows" (dotless i), so the bundled
        // native library never resolves. Pin the test JVM to en-US.
        android.testOptions.unitTests.all { testTask ->
            testTask.jvmArgs("-Duser.language=en", "-Duser.country=US")
        }
    }

}