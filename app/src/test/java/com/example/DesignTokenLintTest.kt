package com.example

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v6 design-system lint: UI files outside ui/theme must take colours, shapes and fonts from tokens.
 */
class DesignTokenLintTest {
    private val allow = setOf("DRComponents.kt", "HudLayout.kt", "ItemSlot.kt")
    private val rules = listOf(
        Regex("""Color\(0x""") to "raw Color(0x…) — use DR.*",
        Regex("""RoundedCornerShape\(""") to "RoundedCornerShape — use DRShape.*",
        Regex("""FontFamily\.Default""") to "FontFamily.Default — use DRType.*",
        Regex("""fontFamily\s*=\s*Poppins""") to "inline Poppins — use DRType.*",
        Regex("""Toast\.makeText""") to "Android Toast — use DRToast",
        Regex("""AlertDialog\(""") to "AlertDialog — use DRDialog"
    )

    @Test
    fun `ui code uses v6 tokens only`() {
        val root = listOf(File("src/main/java/com/example/ui"), File("app/src/main/java/com/example/ui")).first { it.exists() }
        val problems = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "theme" !in it.path && it.name !in allow }
            .forEach { f ->
                f.readLines().forEachIndexed { i, line ->
                    if (line.trimStart().startsWith("import ") || line.trimStart().startsWith("//")) return@forEachIndexed
                    for ((re, why) in rules) if (re.containsMatchIn(line)) problems += "${f.name}:${i + 1} $why"
                }
            }
        assertTrue("Design token violations:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
