package com.appsonair.applink.services

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Keeps the SDK's notion of "now" independent of the device clock, which is the one input in the
 * attributionTtl comparison that the user can edit from Settings.
 *
 * Every API response carries an RFC 1123 `Date` header stamped by the server. The difference
 * between it and the device clock is persisted as an offset, so a device whose clock is simply
 * wrong — never synced, reset by a flat battery — still measures attributionTtl correctly.
 *
 * Alongside it a high-water mark of the newest corrected time ever observed is persisted. Real
 * time only moves forward, so a corrected now that falls behind that mark means the clock was
 * rewound between two observations.
 *
 * **Known limit, deliberately not papered over.** The offset is only refreshed when a request
 * happens, so a clock moved *after* the last capture drags the corrected time with it. The
 * high-water mark catches that only once the rewound time falls behind something already seen —
 * it cannot tell "the app was closed for 3 minutes" from "the app was closed for 3 hours and the
 * clock was wound back". Closing that gap needs the backend to decide expiry; it is not solvable
 * on the device.
 */
internal object ServerTimeService {

    private const val PREFS = "Referral"
    private const val OFFSET_KEY = "server_time_offset"
    private const val HIGH_WATER_KEY = "server_time_high_water"

    private var appContext: Context? = null

    @Volatile
    private var offsetMillis = 0L

    @Volatile
    private var highWaterMillis = 0L

    /** Restores the persisted offset and high-water mark. Safe to call more than once. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        offsetMillis = prefs.getLong(OFFSET_KEY, 0L)
        highWaterMillis = prefs.getLong(HIGH_WATER_KEY, 0L)
        Log.d("ServerTimeService", "Restored offset=${offsetMillis}ms highWater=$highWaterMillis")
    }

    /**
     * Records the server clock from a response `Date` header. Called for every API response, so a
     * malformed or absent header is ignored rather than logged loudly.
     */
    fun recordServerDate(header: String?) {
        val serverMillis = header?.let(::parseHttpDate) ?: return
        offsetMillis = serverMillis - System.currentTimeMillis()
        persist(OFFSET_KEY, offsetMillis)
        observe(serverMillis)
    }

    /** The device clock corrected by the last known server offset. */
    fun now(): Long = System.currentTimeMillis() + offsetMillis

    /** True when corrected time has moved behind the newest time already observed. */
    fun hasClockRewound(): Boolean = highWaterMillis > 0L && now() < highWaterMillis

    /** Advances the high-water mark. Never moves it backwards. */
    fun observe(correctedMillis: Long = now()) {
        if (correctedMillis > highWaterMillis) {
            highWaterMillis = correctedMillis
            persist(HIGH_WATER_KEY, correctedMillis)
        }
    }

    /**
     * Parses the RFC 1123 form HTTP requires for `Date`. `SimpleDateFormat` is not thread safe and
     * responses arrive on arbitrary threads, so the instance is built per call.
     */
    private fun parseHttpDate(header: String): Long? {
        return try {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("GMT") }
                .parse(header)?.time
        } catch (e: Exception) {
            Log.d("ServerTimeService", "Unparseable Date header: $header")
            null
        }
    }

    private fun persist(key: String, value: Long) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putLong(key, value)?.apply()
    }
}
