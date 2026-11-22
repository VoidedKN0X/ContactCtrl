package com.vknxworks.contactctrl

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Bundle

/**
 * Opens a .vcf file another app shares or opens with ContactCtrl. Its only job is to let Android check the link:
 * on Android 15+ the system refuses to start it when the app that sent the link can't read that file itself
 * (`requireContentUriPermissionFromCaller` in the manifest), so no app can make ContactCtrl read a file the app
 * couldn't read on its own. Then it hands the same link to [MainActivity] (as clip data, remembered in
 * [AppContainer.pendingImport]), which shows the import as before.
 */
class ImportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        if (data != null && data.scheme.equals("content", ignoreCase = true)) {
            // MainActivity only imports the link it was told about here, never one another app sends it directly.
            container.pendingImport = data
            val open = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            open.clipData = ClipData.newRawUri("", data)
            runCatching { startActivity(open) }
        }
        finish()
    }
}
