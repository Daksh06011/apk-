package com.phonetemp.app.power

import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock

/**
 * Charging power that accounts for how many cells the battery has.
 *
 * Android reports battery voltage and current, but on dual-cell (2S, series) phones - most fast
 * charging OnePlus/OPPO/realme models - the voltage is one cell's (~4.2 V) while the pack charges at
 * twice that, so current x voltage shows about half the real power. No API reports the cell count,
 * so it's measured:
 *
 *   While charging, the reported power (I x V) is integrated over time, and compared with the energy
 *   the battery actually gained, from its rated capacity and the percent it rose (between two exact
 *   percent steps, so rounding can't skew it). About 1x means single-cell, about 2x dual-cell.
 *
 * The answer is kept (SharedPreferences), rechecked on later charges, and a pack that already reports
 * its full voltage (above 6 V) needs no correction. The headline while charging is the estimated
 * charger input: power into the battery over typical fast-charge conversion efficiency.
 *
 * Hooked in by build.sh: BatteryReading's Normalize.estimateWatts call and the Power card caption.
 */
object ChargePower {
    /** 0 = not known yet, else 1 or 2. */
    @Volatile var cells: Int = 0
        private set

    /** Share of charger power that reaches the battery (charge pump + cable + phone circuitry). */
    const val INPUT_EFFICIENCY = 0.92f

    private const val NOMINAL_CELL_V = 3.87f   // rated-capacity energy is quoted at nominal voltage
    private const val MIN_PERCENT_STEP = 2
    private const val MIN_WINDOW_MS = 60_000L

    private var loaded = false
    private var capacityMah = 0.0
    private var lastCharging = false

    // measurement window, opened on an exact percent step
    private var lastLevel = -1
    private var windowStartLevel = -1
    private var windowStartMs = 0L
    private var reportedWh = 0.0
    private var lastSampleMs = 0L

    /** Replacement for Normalize.estimateWatts(amps, volts). */
    @JvmStatic
    @Synchronized
    fun watts(amps: Float?, volts: Float?): Float? {
        if (amps == null || volts == null || amps <= 0f || volts <= 0f) return null
        val raw = amps * volts
        if (raw < 0.01f || raw > 250f) return null
        val ctx = appContext()
        if (ctx != null) {
            ensureLoaded(ctx)
            observe(ctx, raw)
        }
        val battery = raw * factorFor(volts)
        val shown = if (lastCharging) battery / INPUT_EFFICIENCY else battery
        return shown.takeIf { it in 0.01f..250f }
    }

    /** Caption under the Power card's number. */
    @JvmStatic
    fun caption(): String {
        val cellText = when (cells) {
            2 -> "dual-cell"
            1 -> "single-cell"
            else -> if (lastCharging) "measuring cells…" else "cells: charge to detect"
        }
        return if (lastCharging) "charger input (est.) · $cellText" else "battery draw · $cellText"
    }

    /** How much to scale current x voltage by: a pack reporting its full voltage needs none. */
    fun factorFor(volts: Float): Int = if (volts > 6f) 1 else cells.coerceAtLeast(1)

    /** Pure decision rule (unit-tested): measured-energy / reported-energy ratio -> cells, or null. */
    fun cellsFromRatio(ratio: Double): Int? = when {
        ratio in 0.55..1.45 -> 1
        ratio in 1.55..2.7 -> 2
        else -> null
    }

    // ------------------------------------------------------------------ measurement

    private fun observe(ctx: Context, rawWatts: Float) {
        val bm = ctx.getSystemService(BatteryManager::class.java) ?: return
        val charging = bm.isCharging
        lastCharging = charging
        val now = SystemClock.elapsedRealtime()
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (!charging || level !in 1..99 || capacityMah <= 0) { resetWindow(); return }

        // integrate what current x voltage claims went into the battery
        if (windowStartLevel >= 0 && lastSampleMs > 0) {
            val dt = now - lastSampleMs
            if (dt in 1..15_000) reportedWh += rawWatts * dt / 3_600_000.0 else resetWindow()
        }
        lastSampleMs = now

        if (lastLevel >= 0 && level != lastLevel) {
            if (windowStartLevel < 0 || level < windowStartLevel) {
                // first exact percent step: open the window here
                windowStartLevel = level; windowStartMs = now; reportedWh = 0.0
            } else if (level - windowStartLevel >= MIN_PERCENT_STEP && now - windowStartMs >= MIN_WINDOW_MS) {
                val gainedWh = capacityMah / 1000.0 * NOMINAL_CELL_V * (level - windowStartLevel) / 100.0
                cellsFromRatio(gainedWh / reportedWh)?.let { save(ctx, it) }
                windowStartLevel = level; windowStartMs = now; reportedWh = 0.0
            }
        }
        lastLevel = level
    }

    private fun resetWindow() {
        windowStartLevel = -1; reportedWh = 0.0; lastSampleMs = 0L; lastLevel = -1
    }

    private fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        cells = prefs(ctx).getInt(KEY_CELLS, 0)
        capacityMah = ratedCapacityMah(ctx)
    }

    private fun save(ctx: Context, n: Int) {
        if (n == cells) return
        cells = n
        prefs(ctx).edit().putInt(KEY_CELLS, n).apply()
    }

    /** Rated capacity from the platform power profile (what Settings > Battery uses), in mAh. */
    private fun ratedCapacityMah(ctx: Context): Double = runCatching {
        val profile = Class.forName("com.android.internal.os.PowerProfile")
            .getConstructor(Context::class.java).newInstance(ctx)
        profile.javaClass.getMethod("getBatteryCapacity").invoke(profile) as Double
    }.getOrNull()?.takeIf { it in 1000.0..20000.0 } ?: 0.0

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("charge_power", Context.MODE_PRIVATE)

    private fun appContext(): Context? = runCatching {
        Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Context
    }.getOrNull()

    private const val KEY_CELLS = "cells"
}
