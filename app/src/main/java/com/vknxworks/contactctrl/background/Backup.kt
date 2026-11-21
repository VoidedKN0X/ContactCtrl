package com.vknxworks.contactctrl.background

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.ui.common.hasPermission
import com.vknxworks.contactctrl.ui.contacts.writeVCards
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The scheduled .vcf backup: every contact in one file in the folder you picked, daily or weekly. Android's job
 * scheduler runs it when the battery isn't low (it may wait a while for that) and keeps it through restarts.
 * Only files this backup made itself (named `contactctrl-backup-….vcf`) are ever deleted, to keep the number
 * you chose; nothing else in the folder is touched.
 */
object BackupScheduler {
    private const val JOB_ID = 4101

    suspend fun schedule(context: Context) {
        val s = context.container.settings.current()
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        if (!s.backupOn || s.backupFolder.isEmpty()) {
            js.cancel(JOB_ID)
            return
        }
        val interval = s.backupEvery.days * 24 * 60 * 60_000L
        val existing = js.getPendingJob(JOB_ID)
        if (existing != null && existing.intervalMillis == interval) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(context, BackupJob::class.java))
            .setPeriodic(interval)
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .build()
        runCatching { js.schedule(info) }
    }

    /**
     * Writes one backup now. True when the file was written. Records the result in the settings so the
     * Settings page can show it.
     */
    suspend fun runOnce(context: Context): Boolean {
        val c = context.container
        val s = c.settings.current()
        val ok = s.backupFolder.isNotEmpty() && context.hasPermission(Manifest.permission.READ_CONTACTS) &&
            runCatching { write(context, Uri.parse(s.backupFolder), s.backupKeep) }.getOrDefault(false)
        c.settings.update { it.copy(lastBackupAt = if (ok) System.currentTimeMillis() else it.lastBackupAt, lastBackupFailed = !ok) }
        return ok
    }

    private suspend fun write(context: Context, tree: Uri, keep: Int): Boolean {
        val resolver = context.contentResolver
        val keys = ArrayList<String>()
        resolver.query(android.provider.ContactsContract.Contacts.CONTENT_URI, arrayOf(android.provider.ContactsContract.Contacts.LOOKUP_KEY), null, null, null)?.use { cur ->
            while (cur.moveToNext()) cur.getString(0)?.let { keys += it }
        }
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".vcf"
        val file = DocumentsContract.createDocument(resolver, dir, "text/x-vcard", name) ?: return false
        val written = runCatching { resolver.openOutputStream(file, "wt")?.use { writeVCards(context, keys, it) } ?: -1 }.getOrDefault(-1)
        if (written < 0 || (written == 0 && keys.isNotEmpty())) {
            // A half-written file of our own from this very run: remove it so it isn't mistaken for a backup.
            runCatching { DocumentsContract.deleteDocument(resolver, file) }
            return false
        }
        prune(context, tree, keep)
        return true
    }

    /** Keeps the newest [keep] of ContactCtrl's own backup files in the folder. */
    private fun prune(context: Context, tree: Uri, keep: Int) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val ours = ArrayList<Pair<String, String>>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val id = cur.getString(0) ?: continue
                val n = cur.getString(1) ?: continue
                if (NAME.matches(n)) ours += id to n
            }
        }
        // The names sort by date and time; the newest come last.
        ours.sortBy { it.second }
        for ((id, _) in ours.dropLast(keep)) {
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    private const val PREFIX = "contactctrl-backup-"
    private val NAME = Regex("^contactctrl-backup-\\d{4}-\\d{2}-\\d{2}-\\d{4}( \\(\\d+\\))?\\.vcf$")
}

/** Runs the scheduled backup. */
class BackupJob : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = CoroutineScope(Dispatchers.IO).launch {
            val ok = runCatching { BackupScheduler.runOnce(applicationContext) }.getOrDefault(false)
            jobFinished(params, !ok)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }
}
