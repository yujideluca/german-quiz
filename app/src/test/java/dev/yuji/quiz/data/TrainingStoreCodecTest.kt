package dev.yuji.quiz.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingStoreCodecTest {
    @Test fun roundTrips() {
        val h = mapOf(20000L to 12, 20001L to 100)
        assertEquals(h, decodeHistory(encodeHistory(h)))
        assertEquals(emptyMap<Long, Int>(), decodeHistory(encodeHistory(emptyMap())))
    }

    @Test fun malformedEntriesAreSkippedNotFatal() {
        assertEquals(mapOf(5L to 3), decodeHistory("5:3;bad;7:x;8:0;:4;9:1:2"))
    }
}
