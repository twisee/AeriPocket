package com.controleco.app

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Verify exclusion with Android's own backup-rule parser, rather than duplicating its XML logic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupPrivacyTest {
    @Test fun cloudAndDeviceTransferExcludeLedgerSettingsAndCaptureFiles() {
        val app = RuntimeEnvironment.getApplication()
        // Robolectric 4.14 leaves this Android 12+ ApplicationInfo field at 0 despite the manifest.
        // Supply the compiled rules resource so the real framework parser can exercise both destinations.
        ApplicationInfo::class.java.getField("dataExtractionRulesRes")
            .setInt(app.applicationInfo, R.xml.data_extraction_rules)
        val device = app.createDeviceProtectedStorageContext()
        // Match the scanner's storage contexts; Robolectric gives its default context a separate path.
        val credential = Context::class.java.getMethod("createCredentialProtectedStorageContext")
            .invoke(app) as Context
        val protectedFiles = listOf(credential, device).flatMap { context -> listOf(
            context.getDatabasePath("privacy-test.db"),
            File(context.dataDir, "shared_prefs/settings.xml"),
            File(context.filesDir, "diagnostics/privacy-test.jsonl"),
        ) } + File(app.getExternalFilesDir(null), "privacy-test.csv")
        val type = Class.forName("android.app.backup.FullBackup\$BackupScheme")
        val constructor = type.getDeclaredConstructor(Context::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val parse = type.getDeclaredMethod("maybeParseAndGetCanonicalExcludePaths").apply { isAccessible = true }
        // Android BackupDestination: CLOUD=0, DEVICE_TRANSFER=1.
        for (destination in listOf(0, 1)) {
            val scheme = constructor.newInstance(app, destination)
            val excluded = (parse.invoke(scheme) as Collection<*>).map { entry ->
                entry!!.javaClass.getMethod("getPath").invoke(entry) as String
            }.map { File(it).canonicalFile.toPath() }
            protectedFiles.forEach { file ->
                assertTrue("Backup destination $destination must exclude ${file.name}",
                    excluded.any { file.canonicalFile.toPath().startsWith(it) })
            }
        }
    }

    @Test @Config(sdk = [29])
    fun olderAndroidStillOptsOutOfSystemBackup() {
        val info = RuntimeEnvironment.getApplication().applicationInfo
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
