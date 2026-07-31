package com.shelfie.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shelfie.core.model.ScreenshotCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A persisted sorting rule, decoupled from the classifier's own type. */
data class StoredRule(
    val id: Long,
    val keyword: String,
    val category: ScreenshotCategory,
    val enabled: Boolean = true,
)

/**
 * Storage for user sorting rules.
 *
 * Encoded as a single delimited string rather than pulling in a serialisation
 * library, because the shape is trivial and stable. [RuleCodec] holds the
 * encoding and is unit-tested independently.
 */
@Singleton
class UserRuleStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val rules: Flow<List<StoredRule>> =
        dataStore.data.map { prefs -> RuleCodec.decodeAll(prefs[KEY_RULES].orEmpty()) }

    suspend fun add(keyword: String, category: ScreenshotCategory) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return

        dataStore.edit { prefs ->
            val existing = RuleCodec.decodeAll(prefs[KEY_RULES].orEmpty())

            // Re-pointing an existing keyword should update it, not stack a
            // second conflicting rule for the same term.
            val withoutDuplicate = existing.filterNot { it.keyword.equals(trimmed, true) }
            val nextId = (existing.maxOfOrNull { it.id } ?: 0L) + 1L

            val updated = withoutDuplicate + StoredRule(nextId, trimmed, category)
            prefs[KEY_RULES] = RuleCodec.encodeAll(updated)
        }
    }

    suspend fun remove(id: Long) {
        dataStore.edit { prefs ->
            val remaining = RuleCodec.decodeAll(prefs[KEY_RULES].orEmpty())
                .filterNot { it.id == id }
            prefs[KEY_RULES] = RuleCodec.encodeAll(remaining)
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dataStore.edit { prefs ->
            val updated = RuleCodec.decodeAll(prefs[KEY_RULES].orEmpty())
                .map { if (it.id == id) it.copy(enabled = enabled) else it }
            prefs[KEY_RULES] = RuleCodec.encodeAll(updated)
        }
    }

    suspend fun current(): List<StoredRule> = rules.first()

    private companion object {
        val KEY_RULES = stringPreferencesKey("user_rules_v1")
    }
}

/**
 * Encoding for [StoredRule].
 *
 * Records are separated by `\n` and fields by `\u001F` (unit separator), a
 * control character that cannot appear in a keyword the user typed. Unparseable
 * records are dropped rather than throwing — a corrupt preference must never
 * prevent the app from starting.
 */
internal object RuleCodec {

    private const val FIELD = '\u001F'
    private const val RECORD = '\n'

    fun encodeAll(rules: List<StoredRule>): String =
        rules.joinToString(RECORD.toString()) { rule ->
            listOf(
                rule.id.toString(),
                rule.keyword.replace(FIELD.toString(), "").replace(RECORD.toString(), " "),
                rule.category.name,
                rule.enabled.toString(),
            ).joinToString(FIELD.toString())
        }

    fun decodeAll(encoded: String): List<StoredRule> {
        if (encoded.isBlank()) return emptyList()

        return encoded.split(RECORD).mapNotNull { record ->
            val parts = record.split(FIELD)
            if (parts.size < 4) return@mapNotNull null

            val id = parts[0].toLongOrNull() ?: return@mapNotNull null
            val keyword = parts[1]
            if (keyword.isBlank()) return@mapNotNull null

            val category = runCatching { ScreenshotCategory.valueOf(parts[2]) }.getOrNull()
                ?: return@mapNotNull null

            StoredRule(
                id = id,
                keyword = keyword,
                category = category,
                enabled = parts[3].toBooleanStrictOrNull() ?: true,
            )
        }
    }
}
