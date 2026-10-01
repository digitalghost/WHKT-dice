package com.example.helloworld

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class RosterStore(context: Context) {
    private val preferences = context.getSharedPreferences("saved_rosters", Context.MODE_PRIVATE)

    fun all(): List<SavedRoster> {
        migrateLegacyRosters()
        return decodeRosterList(preferences.getString(KEY_ROSTERS, null))
            .sortedByDescending { it.updatedAt }
    }

    fun load(rosterId: String): SavedRoster? = all().firstOrNull { it.rosterId == rosterId }

    fun createDraft(team: KillTeamCatalog): SavedRoster = SavedRoster(
        rosterId = UUID.randomUUID().toString(),
        teamId = team.id,
        name = "我的${team.name}小队",
        members = mutableListOf()
    )

    /** Compatibility entry point for the original one-roster-per-catalog editor. */
    fun load(team: KillTeamCatalog): SavedRoster =
        all().firstOrNull { it.teamId == team.id } ?: createDraft(team)

    fun save(roster: SavedRoster): SavedRoster {
        val saved = roster.copy(
            members = roster.members.toMutableList(),
            updatedAt = System.currentTimeMillis()
        )
        val rosters = all().toMutableList()
        val index = rosters.indexOfFirst { it.rosterId == saved.rosterId }
        if (index >= 0) rosters[index] = saved else rosters.add(saved)
        writeAll(rosters)
        return saved
    }

    fun duplicate(rosterId: String): SavedRoster? {
        val source = load(rosterId) ?: return null
        val copy = source.copy(
            rosterId = UUID.randomUUID().toString(),
            name = "${source.name} · 副本",
            members = source.members.map { member ->
                member.copy(id = UUID.randomUUID().toString())
            }.toMutableList(),
            updatedAt = System.currentTimeMillis()
        )
        writeAll(all() + copy)
        return copy
    }

    fun delete(rosterId: String) {
        val remaining = all().filterNot { it.rosterId == rosterId }
        writeAll(remaining)
        if (activeRosterId() == rosterId) {
            preferences.edit().remove(KEY_ACTIVE_ROSTER).apply()
        }
    }

    fun activeRosterId(): String? {
        migrateLegacyRosters()
        val activeId = preferences.getString(KEY_ACTIVE_ROSTER, null) ?: return null
        return activeId.takeIf { id ->
            decodeRosterList(preferences.getString(KEY_ROSTERS, null)).any { it.rosterId == id }
        }
    }

    fun activeRoster(): SavedRoster? = activeRosterId()?.let(::load)

    fun setActiveRoster(rosterId: String) {
        require(all().any { it.rosterId == rosterId }) { "Unknown roster: $rosterId" }
        preferences.edit().putString(KEY_ACTIVE_ROSTER, rosterId).apply()
    }

    fun selectedTeamId(): String =
        preferences.getString(KEY_SELECTED_TEAM, RosterCatalog.teams.first().id)
            ?: RosterCatalog.teams.first().id

    fun setSelectedTeam(teamId: String) {
        preferences.edit().putString(KEY_SELECTED_TEAM, teamId).apply()
    }

    private fun migrateLegacyRosters() {
        if (preferences.contains(KEY_ROSTERS)) return

        val migrated = RosterCatalog.teams.mapNotNull { team ->
            val source = preferences.getString(legacyRosterKey(team.id), null) ?: return@mapNotNull null
            runCatching {
                decodeRoster(JSONObject(source), team.id, UUID.randomUUID().toString())
            }.getOrNull()
        }
        val legacySelectedTeam = selectedTeamId()
        val initiallyActive = migrated.firstOrNull { it.teamId == legacySelectedTeam }
            ?: migrated.firstOrNull()

        preferences.edit()
            .putString(KEY_ROSTERS, encodeRosterList(migrated).toString())
            .putBoolean(KEY_MIGRATED, true)
            .apply {
                if (initiallyActive != null) putString(KEY_ACTIVE_ROSTER, initiallyActive.rosterId)
            }
            .apply()
    }

    private fun writeAll(rosters: List<SavedRoster>) {
        preferences.edit().putString(KEY_ROSTERS, encodeRosterList(rosters).toString()).apply()
    }

    private fun decodeRosterList(source: String?): List<SavedRoster> {
        if (source.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(source)
            List(array.length()) { index ->
                val root = array.getJSONObject(index)
                decodeRoster(
                    root = root,
                    fallbackTeamId = root.optString("teamId", RosterCatalog.teams.first().id),
                    fallbackRosterId = UUID.randomUUID().toString()
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun decodeRoster(root: JSONObject, fallbackTeamId: String, fallbackRosterId: String): SavedRoster {
        val teamId = root.optString("teamId", fallbackTeamId)
        val team = RosterCatalog.team(teamId)
        val membersJson = root.optJSONArray("members") ?: JSONArray()
        val members = MutableList(membersJson.length()) { index ->
            val member = membersJson.getJSONObject(index)
            val weaponsJson = member.optJSONArray("weaponIds") ?: JSONArray()
            RosterMember(
                id = member.optString("id", UUID.randomUUID().toString()),
                operativeId = member.getString("operativeId"),
                callsign = member.optString("callsign"),
                weaponIds = List(weaponsJson.length()) { weaponsJson.getString(it) },
                customAvatarUri = member.optString("customAvatarUri").takeIf(String::isNotBlank)
            )
        }
        return SavedRoster(
            rosterId = root.optString("rosterId", fallbackRosterId),
            teamId = teamId,
            name = root.optString("name", "我的${team.name}小队"),
            members = members,
            updatedAt = root.optLong("updatedAt", 0L)
        )
    }

    private fun encodeRosterList(rosters: List<SavedRoster>) = JSONArray().apply {
        rosters.forEach { put(encodeRoster(it)) }
    }

    private fun encodeRoster(roster: SavedRoster) = JSONObject()
        .put("rosterId", roster.rosterId)
        .put("teamId", roster.teamId)
        .put("name", roster.name)
        .put("updatedAt", roster.updatedAt)
        .put("members", JSONArray().apply {
            roster.members.forEach { member ->
                put(
                    JSONObject()
                        .put("id", member.id)
                        .put("operativeId", member.operativeId)
                        .put("callsign", member.callsign)
                        .put("weaponIds", JSONArray(member.weaponIds))
                        .put("customAvatarUri", member.customAvatarUri.orEmpty())
                )
            }
        })

    private fun legacyRosterKey(teamId: String) = "roster_$teamId"

    private companion object {
        const val KEY_ROSTERS = "rosters_v2"
        const val KEY_ACTIVE_ROSTER = "active_roster_id"
        const val KEY_SELECTED_TEAM = "selected_team"
        const val KEY_MIGRATED = "rosters_v2_migrated"
    }
}
