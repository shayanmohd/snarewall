package com.mohdshayan.snarewall.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.mohdshayan.snarewall.data.prefs.AppPrefs
import com.mohdshayan.snarewall.game.SaveCodec
import com.mohdshayan.snarewall.game.SaveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes the save file for the share sheet, and reads one back from the system picker. */
class SaveTransfer(
    private val context: Context,
    private val repo: ProgressRepository,
    private val prefs: AppPrefs,
) {
    sealed interface ReadResult {
        data class Ok(val file: SaveFile) : ReadResult
        data object NotASave : ReadResult
        data object NewerVersion : ReadResult
    }

    /** Builds the file and returns a share intent for it. */
    suspend fun exportIntent(): Intent = withContext(Dispatchers.IO) {
        val (levels, resume, daily) = repo.snapshotRows()
        val file = SaveFile(
            exportedAt = System.currentTimeMillis(),
            levelProgress = levels,
            resume = resume,
            dailyScores = daily,
            settings = prefs.exportMap(),
        )
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = "snarewall-save-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".json"
        val out = File(dir, name)
        out.writeText(SaveCodec.encode(file))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", out)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(send, "Export save")
    }

    suspend fun read(uri: Uri): ReadResult = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                // A real save is a few kilobytes; refuse anything absurd before reading it all.
                val bytes = stream.readNBytesCompat(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) null else String(bytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        } ?: return@withContext ReadResult.NotASave
        when (val d = SaveCodec.decode(text)) {
            is SaveCodec.Decoded.Ok -> ReadResult.Ok(d.file)
            SaveCodec.Decoded.NewerVersion -> ReadResult.NewerVersion
            SaveCodec.Decoded.NotASave -> ReadResult.NotASave
        }
    }

    suspend fun apply(file: SaveFile) = withContext(Dispatchers.IO) {
        repo.replaceAll(file.levelProgress, file.resume, file.dailyScores)
        prefs.importMap(file.settings)
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (total < limit) {
            val n = read(chunk, 0, minOf(chunk.size, limit - total))
            if (n < 0) break
            buf.write(chunk, 0, n)
            total += n
        }
        return buf.toByteArray()
    }

    companion object {
        const val MAX_BYTES = 2_000_000
    }
}
