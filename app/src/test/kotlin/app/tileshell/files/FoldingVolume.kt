package app.tileshell.files

import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException

/**
 * A volume that folds names the way shared storage does (the GATE review's N2, measured on the emulator): default
 * ignorable code points are dropped, case is folded, `ß` is `ss` and `ſ` is `s` — so `.Teßera`, `.Tess<U+200B>era` and
 * `.TESSERA` all lead to `.Tessera`. The host's file system does not fold, so the answers are handed in: this is the
 * [FileIdentity] / [FolderNames] port a JVM test gives the rules. Only the port's answers fold — a write through an
 * alias still fails on the host, which is why the write tests also use a link as the alias ([FileOpsIdentityTest]).
 */
class FoldingVolume : FolderNames {
    private fun fold(name: String): String =
        name.filterNot { it == '​' || it == '‌' || it == '‍' || it == '­' || it == '﻿' }.lowercase().replace("ß", "ss").replace("ſ", "s")

    /** The entry [file] leads to on this volume, or null. */
    fun resolve(file: File): File? {
        var at = File("/")
        for (segment in file.absolutePath.split('/').filter { it.isNotEmpty() }) {
            val exact = File(at, segment)
            at = if (FilePaths.existsNoFollow(exact)) exact else at.listFiles()?.firstOrNull { fold(it.name) == fold(segment) } ?: return null
        }
        return at
    }

    override fun exists(file: File): Boolean = resolve(file) != null
    override fun same(a: File, b: File): Boolean {
        if (a == b) return true
        val ra = resolve(a) ?: return false
        val rb = resolve(b) ?: return false
        return runCatching { Files.isSameFile(ra.toPath(), rb.toPath()) }.getOrDefault(false)
    }
    override fun names(dir: File): List<String>? = resolve(dir)?.list()?.toList()
    override fun rename(from: File, to: File) {
        val src = resolve(from) ?: throw NoSuchFileException(from.path)
        val there = resolve(to)
        if (there == src) return
        if (there != null) throw FileAlreadyExistsException(to.path)
        Files.move(src.toPath(), to.toPath())
    }
}
