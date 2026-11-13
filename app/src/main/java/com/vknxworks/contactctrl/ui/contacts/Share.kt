package com.vknxworks.contactctrl.ui.contacts

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.data.contacts.VCard
import com.vknxworks.contactctrl.ui.common.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/** Writes the vCards of these contacts to [out]. Returns how many were written. */
suspend fun writeVCards(context: Context, lookupKeys: List<String>, out: OutputStream, progress: (Int) -> Unit = {}): Int =
    withContext(Dispatchers.IO) {
        var n = 0
        out.bufferedWriter(Charsets.UTF_8).let { w ->
            lookupKeys.forEachIndexed { i, key ->
                val card = VCard.export(context.contentResolver, key)
                if (!card.isNullOrBlank()) {
                    w.write(card.trimEnd())
                    w.write("\r\n")
                    n++
                }
                if (i % 20 == 0) progress(i)
            }
            w.flush()
        }
        n
    }

/**
 * Shares contacts as a .vcf file, readable only by the app you pick and only for that share. The file sits in
 * ContactCtrl's private cache and is replaced on the next share.
 */
suspend fun shareContacts(context: Context, lookupKeys: List<String>, name: String?) {
    if (lookupKeys.isEmpty()) return
    val uri: Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val safe = (name ?: "contacts").replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().ifBlank { "contacts" }.take(60)
            val file = File(dir, "$safe.vcf")
            val written = file.outputStream().use { writeVCards(context, lookupKeys, it) }
            if (written == 0) null else FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
    }
    if (uri == null) {
        Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/x-vcard")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri("", uri)
    context.launch(Intent.createChooser(send, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
