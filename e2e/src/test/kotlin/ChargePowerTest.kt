import com.phonetemp.app.ohaptics.HapticGain
import com.phonetemp.app.power.ChargePower
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cell-count rule and the Tacta output gain, as shipped. */
class ChargePowerTest {
    @Test fun ratioDecidesCells() {
        // energy the percent rise implies / energy current x voltage reported
        assertEquals(1, ChargePower.cellsFromRatio(0.94))   // single cell (charging V above nominal)
        assertEquals(1, ChargePower.cellsFromRatio(1.10))
        assertEquals(2, ChargePower.cellsFromRatio(1.90))   // dual cell: I x V_cell is half the truth
        assertEquals(2, ChargePower.cellsFromRatio(2.20))
        assertNull(ChargePower.cellsFromRatio(1.50))        // ambiguous: keep measuring
        assertNull(ChargePower.cellsFromRatio(4.0))         // nonsense window: ignore
    }

    @Test fun packsReportingFullVoltageAreNotDoubled() {
        assertEquals(1, ChargePower.factorFor(8.4f))
    }

    @Test fun userScenario_dualCellAt17W() {
        // 1.97 A at a reported 4.19 V is 8.25 W; a 2S pack is really at ~8.4 V -> ~16.5 W into the
        // battery, ~17.9 W from the charger at 92 % conversion.
        val intoBattery = 1.97f * 4.19f * 2
        val fromCharger = intoBattery / ChargePower.INPUT_EFFICIENCY
        assertTrue("$intoBattery", intoBattery in 16f..17f)
        assertTrue("$fromCharger", fromCharger in 17f..18.5f)
    }

    @Test fun tactaGainLiftsFaintHitsAndCapsStrongOnes() {
        assertEquals(0.686f, HapticGain.apply(0.36f), 0.01f)   // faintest snap
        assertEquals(0.788f, HapticGain.apply(0.48f), 0.01f)   // knob detent
        assertEquals(1.0f, HapticGain.apply(0.92f), 0.0001f)   // drop impact
        assertTrue(HapticGain.apply(0.5f) > 0.5f)
    }
}
