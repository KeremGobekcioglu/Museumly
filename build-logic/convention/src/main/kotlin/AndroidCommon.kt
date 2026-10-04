import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion

fun configureAndroidCommon(android: CommonExtension)
{
    android.compileSdk = 37
    android.defaultConfig.minSdk = 29
    android.compileOptions.sourceCompatibility = JavaVersion.VERSION_11
    android.compileOptions.targetCompatibility = JavaVersion.VERSION_11
}