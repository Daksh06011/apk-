import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Static checks on the shipped APK. These cover what the JVM-based tests can't: how ART will load
 * the dex files (the launch crash was dex 035 rejecting interface default methods).
 */
class ApkIntegrityTest {
    private val patched = ZipFile(File(System.getProperty("apk.path")))
    private val original = ZipFile(File(System.getProperty("apk.original")))

    private fun ZipFile.bytes(name: String) = getInputStream(getEntry(name) ?: error("$name missing")).readBytes()
    private fun ZipFile.dexNames() = entries().asSequence().map { it.name }.filter { it.matches(Regex("classes\\d*\\.dex")) }.toList()

    @Test fun everyDexIsVersion037OrNewer() {
        val versions = patched.dexNames().associateWith { String(patched.bytes(it), 4, 3).toInt() }
        assertTrue("dex versions: $versions", versions.values.all { it >= 37 })
    }

    @Test fun keepsAllOriginalDexAndAddsTheStudioDex() {
        val names = patched.dexNames().toSet()
        assertTrue(names.containsAll(original.dexNames()))
        assertEquals(original.dexNames().size + 1, names.size)
        val studio = patched.bytes("classes10.dex")
        assertTrue("classes10.dex should hold Tacta",
            String(studio, Charsets.ISO_8859_1).contains("Lcom/phonetemp/app/ohaptics/StudioKt;"))
    }

    @Test fun pulseLabCallsTheStudio() {
        // PulseLabScreen's examples column (classes5.dex) must reference StudioKt.OHapticsStudio.
        val dex = String(patched.bytes("classes5.dex"), Charsets.ISO_8859_1)
        assertTrue(dex.contains("Lcom/phonetemp/app/ohaptics/StudioKt;"))
        assertTrue(dex.contains("OHapticsStudio"))
        assertTrue(!String(original.bytes("classes5.dex"), Charsets.ISO_8859_1).contains("OHapticsStudio"))
    }

    @Test fun pulseLabUsesTheRebuiltExamples() {
        val dex = String(patched.bytes("classes5.dex"), Charsets.ISO_8859_1)
        assertTrue(dex.contains("RiseFallExample"))
        assertTrue(dex.contains("DragThresholdPad"))
    }

    @Test fun appIsNamedVtPhoneTemp() {
        // resources.arsc: only the global string pool changes (app_name's value). Same number of
        // strings, and every package, type and entry chunk after the pool is byte-for-byte the same.
        val a = original.bytes("resources.arsc")
        val b = patched.bytes("resources.arsc")
        fun le(x: ByteArray, at: Int) = java.nio.ByteBuffer.wrap(x).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(at)
        assertEquals(le(a, 12 + 8), le(b, 12 + 8))
        val poolEndA = 12 + le(a, 12 + 4)
        val poolEndB = 12 + le(b, 12 + 4)
        assertArrayEquals(a.copyOfRange(poolEndA, a.size), b.copyOfRange(poolEndB, b.size))
        val before = String(a, Charsets.UTF_8)
        val after = String(b, Charsets.UTF_8)
        assertTrue(before.contains("Phone Temp") && !before.contains("VT:Phone Temp"))
        assertTrue(after.contains("VT:Phone Temp"))
        // Home screen header
        assertTrue(String(patched.bytes("classes5.dex"), Charsets.ISO_8859_1).contains("VT:Phone Temp"))
    }

    @Test fun manifestHasItsOwnPackageAndANewerVersion() {
        // Installs beside any earlier PhoneTemp: new package, permission and provider authority;
        // activity class names unchanged; versionCode above the original's 1.
        val strings = String(patched.bytes("AndroidManifest.xml"), Charsets.UTF_16LE)
        assertTrue(strings.contains("com.phonetemp.tacta\u0000"))
        assertTrue(strings.contains("com.phonetemp.tacta.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"))
        assertTrue(strings.contains("com.phonetemp.tacta.androidx-startup"))
        assertTrue(strings.contains("com.phonetemp.app.MainActivity"))
        assertTrue(!Regex("com\\.phonetemp\\.app[.\\-](DYNAMIC|androidx)").containsMatchIn(strings))
        assertTrue(!strings.contains("com.phonetemp.app\u0000"))
        val code = versionCode(patched.bytes("AndroidManifest.xml"))
        assertTrue("versionCode $code", code > 1)
    }

    /** android:versionCode (attr 0x0101021b) from a binary manifest. */
    private fun versionCode(b: ByteArray): Int {
        val buf = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var pos = 8
        var ids = IntArray(0)
        while (pos < b.size) {
            val type = buf.getShort(pos).toInt() and 0xffff
            val size = buf.getInt(pos + 4)
            if (type == 0x0180) ids = IntArray((size - 8) / 4) { buf.getInt(pos + 8 + 4 * it) }
            if (type == 0x0102) {
                val start = buf.getShort(pos + 24).toInt(); val step = buf.getShort(pos + 26).toInt()
                repeat(buf.getShort(pos + 28).toInt()) {
                    val a = pos + 16 + start + it * step
                    val name = buf.getInt(a + 4)
                    if (name in ids.indices && ids[name] == 0x0101021b) return buf.getInt(a + 16)
                }
            }
            pos += size
        }
        error("no versionCode")
    }

    @Test fun keepsEveryNonDexEntry() {
        val skip = Regex("classes\\d*\\.dex|META-INF/.*")
        val want = original.entries().asSequence().map { it.name }.filterNot { it.matches(skip) }.toSet()
        val have = patched.entries().asSequence().map { it.name }.toSet()
        assertEquals(emptySet<String>(), want - have)
    }

    @Test fun nativeLibsAreStoredAndPageAligned() {
        // extractNativeLibs=false: the loader maps .so files straight out of the APK, 16 KiB pages included.
        val raf = java.io.RandomAccessFile(File(System.getProperty("apk.path")), "r")
        patched.entries().asSequence().filter { it.name.endsWith(".so") }.forEach { e ->
            assertEquals("${e.name} must be stored", java.util.zip.ZipEntry.STORED, e.method)
            raf.seek(e.localHeaderOffset())
            val header = ByteArray(30).also { raf.readFully(it) }
            val nameLen = (header[26].toInt() and 0xff) or ((header[27].toInt() and 0xff) shl 8)
            val extraLen = (header[28].toInt() and 0xff) or ((header[29].toInt() and 0xff) shl 8)
            val dataOffset = e.localHeaderOffset() + 30 + nameLen + extraLen
            assertEquals("${e.name} data offset $dataOffset", 0L, dataOffset % 16384)
        }
    }

    /** ZipEntry doesn't expose the local header offset; find it from the central directory. */
    private fun java.util.zip.ZipEntry.localHeaderOffset(): Long = centralOffsets.getValue(name)

    private val centralOffsets: Map<String, Long> by lazy {
        val bytes = File(System.getProperty("apk.path")).readBytes()
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var eocd = bytes.size - 22
        while (buf.getInt(eocd) != 0x06054b50) eocd--
        var p = buf.getInt(eocd + 16)
        val count = buf.getShort(eocd + 10).toInt() and 0xffff
        buildMap {
            repeat(count) {
                val nameLen = buf.getShort(p + 28).toInt() and 0xffff
                val extraLen = buf.getShort(p + 30).toInt() and 0xffff
                val commentLen = buf.getShort(p + 32).toInt() and 0xffff
                put(String(bytes, p + 46, nameLen), buf.getInt(p + 42).toLong() and 0xffffffffL)
                p += 46 + nameLen + extraLen + commentLen
            }
        }
    }
}
