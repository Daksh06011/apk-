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
        assertTrue("classes10.dex should hold O-Haptics Studio",
            String(studio, Charsets.ISO_8859_1).contains("Lcom/phonetemp/app/ohaptics/StudioKt;"))
    }

    @Test fun manifestAndResourcesAreUntouched() {
        assertArrayEquals(original.bytes("AndroidManifest.xml"), patched.bytes("AndroidManifest.xml"))
        assertArrayEquals(original.bytes("resources.arsc"), patched.bytes("resources.arsc"))
    }

    @Test fun keepsEveryNonDexEntry() {
        val skip = Regex("classes\\d*\\.dex|META-INF/.*")
        val want = original.entries().asSequence().map { it.name }.filterNot { it.matches(skip) }.toSet()
        val have = patched.entries().asSequence().map { it.name }.toSet()
        assertEquals(emptySet<String>(), want - have)
    }

    @Test fun nativeLibsAreStoredAndPageAligned() {
        // extractNativeLibs=false: the loader maps .so files straight out of the APK.
        val raf = java.io.RandomAccessFile(File(System.getProperty("apk.path")), "r")
        patched.entries().asSequence().filter { it.name.endsWith(".so") }.forEach { e ->
            assertEquals("${e.name} must be stored", java.util.zip.ZipEntry.STORED, e.method)
            raf.seek(e.localHeaderOffset())
            val header = ByteArray(30).also { raf.readFully(it) }
            val nameLen = (header[26].toInt() and 0xff) or ((header[27].toInt() and 0xff) shl 8)
            val extraLen = (header[28].toInt() and 0xff) or ((header[29].toInt() and 0xff) shl 8)
            val dataOffset = e.localHeaderOffset() + 30 + nameLen + extraLen
            assertEquals("${e.name} data offset $dataOffset", 0L, dataOffset % 4096)
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
