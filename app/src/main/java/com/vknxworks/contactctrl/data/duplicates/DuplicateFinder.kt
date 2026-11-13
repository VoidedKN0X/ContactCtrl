package com.vknxworks.contactctrl.data.duplicates

import java.text.Normalizer

/** One contact as the duplicate finder sees it. */
data class DupContact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
)

/** Why contacts look like the same person: a phone number, an email or a name they share. */
data class DupReason(val kind: Kind, val value: String) {
    enum class Kind { PHONE, EMAIL, NAME }
}

/** Contacts that are probably the same person, and why. */
data class DupGroup(val members: List<DupContact>, val reasons: List<DupReason>) {
    /** Stays the same while the same contacts are in the group (for list keys). */
    val key: String get() = members.map { it.lookupKey }.sorted().joinToString("|")
}

/**
 * Groups likely duplicates: contacts that share a phone number (compared in E.164-like form), an email address
 * (ignoring case), or a name (ignoring case, accents, punctuation, extra spaces and the order of the parts, so
 * "Smith, John" matches "john  smith"). Pairs you marked "not duplicates" don't link contacts. A value shared
 * by very many contacts (an office switchboard, a shared family email) is ignored: that's not one person.
 */
object DuplicateFinder {
    /** A phone number, email or name shared by more contacts than this links none of them. */
    const val MAX_SHARED = 12

    /** Canonical form of an unordered pair, for remembering "not duplicates". */
    fun pairKey(a: String, b: String): String = if (a <= b) "$a\u0000$b" else "$b\u0000$a"

    /** Every pair in a group, as [pairKey]s (what "not duplicates" remembers). */
    fun pairsOf(group: DupGroup): List<String> {
        val keys = group.members.map { it.lookupKey }
        val out = ArrayList<String>()
        for (i in keys.indices) for (j in i + 1 until keys.size) out += pairKey(keys[i], keys[j])
        return out
    }

    /** Lower case, no accents, no punctuation, single spaces, parts in alphabetical order; null without letters. */
    fun nameKey(name: String): String? {
        val folded = Normalizer.normalize(name, Normalizer.Form.NFKD).replace(Regex("\\p{Mn}+"), "").lowercase()
        val parts = folded.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (parts.none { p -> p.any { it.isLetter() } }) return null
        return parts.sorted().joinToString(" ")
    }

    fun emailKey(email: String): String? = email.trim().lowercase().takeIf { it.length in 3..254 && '@' in it && !it.startsWith('@') && !it.endsWith('@') }

    fun find(contacts: List<DupContact>, region: String?, dismissed: Set<String> = emptySet()): List<DupGroup> {
        val n = contacts.size
        val parent = IntArray(n) { it }
        fun root(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        // Buckets: value -> contacts with it.
        val buckets = HashMap<Pair<DupReason.Kind, String>, MutableList<Int>>()
        val shown = HashMap<Pair<DupReason.Kind, String>, String>()
        contacts.forEachIndexed { i, c ->
            val keys = LinkedHashSet<Pair<DupReason.Kind, String>>()
            for (p in c.phones) PhoneNormalizer.normalize(p, region)?.let { k ->
                val key = DupReason.Kind.PHONE to k
                if (keys.add(key)) shown.putIfAbsent(key, p.trim())
            }
            for (e in c.emails) emailKey(e)?.let { k ->
                val key = DupReason.Kind.EMAIL to k
                if (keys.add(key)) shown.putIfAbsent(key, e.trim())
            }
            nameKey(c.name)?.let { k ->
                val key = DupReason.Kind.NAME to k
                if (keys.add(key)) shown.putIfAbsent(key, c.name.trim().replace(Regex("\\s+"), " "))
            }
            for (k in keys) buckets.getOrPut(k) { ArrayList() } += i
        }
        // Links between contacts, with what they share.
        val linkReasons = HashMap<Long, MutableList<DupReason>>()
        for ((key, members) in buckets) {
            if (members.size < 2 || members.size > MAX_SHARED) continue
            val reason = DupReason(key.first, shown[key] ?: key.second)
            for (x in members.indices) for (y in x + 1 until members.size) {
                val a = members[x]
                val b = members[y]
                if (contacts[a].lookupKey == contacts[b].lookupKey) continue
                if (pairKey(contacts[a].lookupKey, contacts[b].lookupKey) in dismissed) continue
                val ra = root(a)
                val rb = root(b)
                if (ra != rb) parent[ra] = rb
                linkReasons.getOrPut(minOf(a, b).toLong() shl 32 or maxOf(a, b).toLong()) { ArrayList() } += reason
            }
        }
        val components = HashMap<Int, MutableList<Int>>()
        for (i in 0 until n) components.getOrPut(root(i)) { ArrayList() } += i
        val reasonsByRoot = HashMap<Int, LinkedHashSet<DupReason>>()
        for ((pair, reasons) in linkReasons) {
            val a = (pair ushr 32).toInt()
            reasonsByRoot.getOrPut(root(a)) { LinkedHashSet() } += reasons
        }
        val collator = java.text.Collator.getInstance().apply { strength = java.text.Collator.PRIMARY }
        return components.entries
            .filter { it.value.size >= 2 }
            .map { (r, members) ->
                val reasons = reasonsByRoot[r].orEmpty().sortedWith(compareBy({ it.kind.ordinal }, { it.value }))
                DupGroup(members.map { contacts[it] }.sortedWith { a, b -> collator.compare(a.name, b.name) }, reasons)
            }
            .sortedWith { a, b -> collator.compare(a.members.first().name, b.members.first().name) }
    }
}
