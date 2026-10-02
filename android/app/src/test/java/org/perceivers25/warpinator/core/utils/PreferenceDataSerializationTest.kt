package org.perceivers25.warpinator.core.utils

import org.junit.Test
import org.perceivers25.warpinator.core.model.preferences.*

class PreferenceDataSerializationTest {
    @Test
    fun `Set SavedFavourite  toJson serialization check`() {
        val set = setOf(SavedFavourite("uuid1"), SavedFavourite("uuid2"))
        val json = set.toJson()
        assert(json == "[{\"uuid\":\"uuid1\"},{\"uuid\":\"uuid2\"}]")
    }

    @Test
    fun `Set SavedFavourite  toJson empty set handling`() {
        val set = setOf<SavedFavourite>()
        val json = set.toJson()
        assert(json == "[]")
    }

    @Test
    fun `List RecentRemote  toJson serialization check`() {
        val list = listOf(
            RecentRemote("host1", "hostname1"),
            RecentRemote("host2", "hostname2"),
        )
        val json = list.toJson()
        assert(json == "[{\"host\":\"host1\",\"hostname\":\"hostname1\"},{\"host\":\"host2\",\"hostname\":\"hostname2\"}]")
    }

    @Test
    fun `List RecentRemote  toJson empty list handling`() {
        val list = listOf<RecentRemote>()
        val json = list.toJson()
        assert(json == "[]")
    }

    @Test
    fun `Set SavedFavourite  fromJson deserialization check`() {
        val json = "[{\"uuid\":\"uuid1\"},{\"uuid\":\"uuid2\"}]"
        val set = savedFavouritesFromJson(json)
        assert(set.contains(SavedFavourite("uuid1")))
        assert(set.contains(SavedFavourite("uuid2")))
    }

    @Test
    fun `Set SavedFavourite  fromJson malformed json exception`() {
        val json = "[{\"uuid\":\"uuid1\"},{\"uuid\":\"uuid2\""
        try {
            savedFavouritesFromJson(json)
            assert(false)
        } catch (e: Exception) {
            assert(true)
        }
    }

    @Test
    fun `Set SavedFavourite  fromJson empty array input`() {
        val json = "[]"
        val set = savedFavouritesFromJson(json)
        assert(set.isEmpty())
    }

    @Test
    fun `List RecentRemote  fromJson deserialization check`() {
        val json =
            "[{\"host\":\"host1\",\"hostname\":\"hostname1\"},{\"host\":\"host2\",\"hostname\":\"hostname2\"}]"
        val list = recentRemotesFromJson(json)
        assert(list.contains(RecentRemote("host1", "hostname1")))
        assert(list.contains(RecentRemote("host2", "hostname2")))
    }

}