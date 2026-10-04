plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace="pl.qazagyt.musicplayer"; compileSdk=36
 defaultConfig { applicationId="pl.qazagyt.musicplayer"; minSdk=26; targetSdk=35; versionCode=8; versionName="8.0" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_21; targetCompatibility=JavaVersion.VERSION_21 }
 kotlinOptions { jvmTarget="21" }
}
dependencies {
 implementation("androidx.core:core-ktx:1.17.0")
 implementation("androidx.activity:activity-ktx:1.10.1")
 implementation("androidx.appcompat:appcompat:1.7.1")
 implementation("androidx.media3:media3-exoplayer:1.8.0")
 implementation("androidx.media3:media3-session:1.8.0")
 implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
 implementation("org.brotli:dec:0.1.2")
}
