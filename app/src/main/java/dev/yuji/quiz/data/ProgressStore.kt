package dev.yuji.quiz.data

import dev.yuji.quiz.debug.LogChannel
import dev.yuji.quiz.debug.NoopStudyLog
import dev.yuji.quiz.debug.StudyLog
import dev.yuji.quiz.debug.debug
import dev.yuji.quiz.domain.CardProgress
import java.io.File
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

interface ProgressStore {
    fun loadAll(): Map<String, CardProgress>
    fun save(progress: CardProgress)
    fun clear()
}

class InMemoryProgressStore(initial: Map<String, CardProgress> = emptyMap()) : ProgressStore {
    private val map = initial.toMutableMap()
    override fun loadAll(): Map<String, CardProgress> = map.toMap()
    override fun save(progress: CardProgress) { map[progress.cardId] = progress }
    override fun clear() = map.clear()
}

/** One JSON file, rewritten atomically (temp file + rename) after every answer. */
class FileProgressStore(private val file: File, private val log: StudyLog = NoopStudyLog) : ProgressStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), CardProgress.serializer())
    private var cache: MutableMap<String, CardProgress>? = null

    override fun loadAll(): Map<String, CardProgress> = cache().toMap()

    override fun save(progress: CardProgress) {
        cache()[progress.cardId] = progress
        flush()
    }

    override fun clear() {
        cache = mutableMapOf()
        file.delete()
    }

    private fun cache(): MutableMap<String, CardProgress> = cache ?: run {
        val loaded = runCatching { json.decodeFromString(serializer, file.readText()) }
            .onFailure { if (file.exists()) log.debug(LogChannel.STORAGE) { "progress file unreadable: ${it.message}" } }
            .getOrDefault(emptyMap())
        loaded.toMutableMap().also { cache = it }
    }

    private fun flush() {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(serializer, cache()))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        log.debug(LogChannel.STORAGE) { "saved ${cache().size} card states to ${file.name}" }
    }
}
