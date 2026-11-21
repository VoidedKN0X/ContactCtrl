package com.vknxworks.contactctrl.background

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import com.vknxworks.contactctrl.MainActivity
import com.vknxworks.contactctrl.R
import com.vknxworks.contactctrl.container
import com.vknxworks.contactctrl.ui.common.hasPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Birthday reminders: once a day, at about the hour you chose, a notification for every contact whose
 * birthday is today. An inexact alarm (no "alarms and reminders" permission, Android may move it by up to an
 * hour to save battery); it is set again after each run, when ContactCtrl starts, after the phone restarts and
 * after the clock or time zone changes. Everything happens on the phone.
 */
object BirthdayReminders {
    private const val CHANNEL = "birthdays"
    private const val ACTION = "com.vknxworks.contactctrl.BIRTHDAYS"
    private const val WINDOW_MS = 60 * 60_000L

    /** Sets (or cancels) the next daily check to match the settings. */
    suspend fun schedule(context: Context) {
        val s = context.container.settings.current()
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        am.cancel(pi)
        if (!s.birthdayReminders) return
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var next = now.toLocalDate().atTime(s.birthdayHour, 0).atZone(zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        am.setWindow(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), WINDOW_MS, pi)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Today's birthdays: lookup key, name and the age turned (when the year is known). */
    fun today(context: Context, day: LocalDate = LocalDate.now()): List<Triple<String, String, Int?>> {
        val out = LinkedHashMap<String, Triple<String, String, Int?>>()
        context.contentResolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.LOOKUP_KEY, Data.DISPLAY_NAME_PRIMARY, Event.START_DATE),
            "${Data.MIMETYPE}=? AND ${Event.TYPE}=?",
            arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val key = c.getString(0) ?: continue
                val date = BirthdayDate.parse(c.getString(2)) ?: continue
                if (!date.isOn(day) || key in out) continue
                out[key] = Triple(key, c.getString(1).orEmpty(), date.ageOn(day))
            }
        }
        return out.values.toList()
    }

    /** Posts today's birthday notifications. Quietly does nothing without the contacts or notification permission. */
    fun notifyToday(context: Context) {
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) return
        if (!context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.birthday_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val list = runCatching { today(context) }.getOrDefault(emptyList())
        for ((key, name, age) in list) {
            val uri = Contacts.getLookupUri(0, key)
            val open = PendingIntent.getActivity(
                context, key.hashCode(),
                Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = if (age != null) context.resources.getQuantityString(R.plurals.birthday_turns, age, age) else context.getString(R.string.birthday_today)
            // On the lock screen only "A birthday today" shows; the name needs the phone unlocked.
            val public = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_birthday)
                .setContentTitle(context.getString(R.string.birthday_public))
                .build()
            val n = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_birthday)
                .setContentTitle(name.ifBlank { context.getString(R.string.birthday_public) })
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
            runCatching { nm.notify("birthday", key.hashCode(), n) }
        }
    }
}

/** The daily birthday alarm, and setting it again after a restart or a clock change. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == "com.vknxworks.contactctrl.BIRTHDAYS" && context.container.settings.current().birthdayReminders) {
                    BirthdayReminders.notifyToday(context)
                }
                BirthdayReminders.schedule(context)
                BackupScheduler.schedule(context)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }
    }
}
