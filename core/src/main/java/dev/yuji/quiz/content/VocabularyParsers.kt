package dev.yuji.quiz.content

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val vocabJson = Json { ignoreUnknownKeys = true }

fun parseNouns(text: String): List<NounEntry> =
    vocabJson.decodeFromString(ListSerializer(NounEntry.serializer()), text)

fun parsePrepositions(text: String): List<PrepositionEntry> =
    vocabJson.decodeFromString(ListSerializer(PrepositionEntry.serializer()), text)

fun parseVerbs(text: String): List<VerbEntry> =
    vocabJson.decodeFromString(ListSerializer(VerbEntry.serializer()), text)

fun parseErrorSentences(text: String): List<ErrorSentenceEntry> =
    vocabJson.decodeFromString(ListSerializer(ErrorSentenceEntry.serializer()), text)
