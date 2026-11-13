package com.vknxworks.contactctrl.data.duplicates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateFinderTest {
    private var next = 1L
    private fun c(name: String, phones: List<String> = emptyList(), emails: List<String> = emptyList()): DupContact {
        val id = next++
        return DupContact(id, "key$id", name, phones, emails)
    }

    private fun names(groups: List<DupGroup>) = groups.map { g -> g.members.map { it.name }.sorted() }

    @Test
    fun sameNumberWrittenDifferently() {
        val a = c("Anna Peeters", phones = listOf("0470 12 34 56"))
        val b = c("Anna P.", phones = listOf("+32 470 12 34 56"))
        val other = c("Bert", phones = listOf("0470 99 99 99"))
        val groups = DuplicateFinder.find(listOf(a, b, other), "BE")
        assertEquals(listOf(listOf("Anna P.", "Anna Peeters")), names(groups))
        assertEquals(listOf(DupReason(DupReason.Kind.PHONE, "0470 12 34 56")), groups.single().reasons)
    }

    @Test
    fun sameEmailIgnoringCase() {
        val groups = DuplicateFinder.find(listOf(c("A", emails = listOf("Anna@Example.com ")), c("B", emails = listOf("anna@example.com"))), "BE")
        assertEquals(1, groups.size)
        assertEquals(DupReason.Kind.EMAIL, groups.single().reasons.single().kind)
    }

    @Test
    fun sameNameAfterNormalising() {
        val groups = DuplicateFinder.find(
            listOf(c("Zoë  Van Damme"), c("van damme, zoe"), c("ZOE VAN-DAMME"), c("Zoe Vandamme")),
            "BE",
        )
        assertEquals(1, groups.size)
        assertEquals(3, groups.single().members.size)
        assertEquals(DupReason.Kind.NAME, groups.single().reasons.single().kind)
        assertEquals("damme van zoe", DuplicateFinder.nameKey("Van Damme, Zoë"))
        assertEquals(DuplicateFinder.nameKey("John Smith"), DuplicateFinder.nameKey("Smith John"))
        assertNull(DuplicateFinder.nameKey("+32 470 12 34 56"))
        assertNull(DuplicateFinder.nameKey("  "))
    }

    @Test
    fun linksChainIntoOneGroupWithAllReasons() {
        val a = c("Anna", phones = listOf("0470123456"))
        val b = c("Anna Work", phones = listOf("+32470123456"), emails = listOf("anna@work.test"))
        val d = c("A. Peeters", emails = listOf("ANNA@work.test"))
        val g = DuplicateFinder.find(listOf(a, b, d), "BE").single()
        assertEquals(3, g.members.size)
        assertEquals(listOf(DupReason.Kind.PHONE, DupReason.Kind.EMAIL), g.reasons.map { it.kind })
    }

    @Test
    fun dismissedPairsDontLink() {
        val a = c("Jan Janssens")
        val b = c("Jan Janssens")
        val dismissed = DuplicateFinder.pairsOf(DuplicateFinder.find(listOf(a, b), "BE").single()).toSet()
        assertTrue(DuplicateFinder.find(listOf(a, b), "BE", dismissed).isEmpty())
        // A new contact that matches still shows, with the one it matches.
        val x = c("Jan Janssens")
        val groups = DuplicateFinder.find(listOf(a, b, x), "BE", dismissed)
        assertEquals(1, groups.size)
        assertEquals(3, groups.single().members.size)
        assertEquals(DuplicateFinder.pairKey("a", "b"), DuplicateFinder.pairKey("b", "a"))
    }

    @Test
    fun valuesSharedByManyContactsAreIgnored() {
        val office = (0 until DuplicateFinder.MAX_SHARED + 1).map { c("Colleague $it", phones = listOf("+32 2 123 45 67")) }
        assertTrue(DuplicateFinder.find(office, "BE").isEmpty())
        val few = office.take(3)
        assertEquals(1, DuplicateFinder.find(few, "BE").size)
    }

    @Test
    fun differentPeopleStayApart() {
        val groups = DuplicateFinder.find(
            listOf(c("Anna", phones = listOf("112")), c("Bert", phones = listOf("112")), c("Carl", emails = listOf("not-an-email")), c("Dirk", emails = listOf("not-an-email"))),
            "BE",
        )
        assertTrue(groups.isEmpty())
        assertFalse(DuplicateFinder.emailKey("@") != null)
    }

    @Test
    fun sameContactTwiceIsNotADuplicate() {
        val a = DupContact(1, "same", "X Y", listOf("0470123456"))
        val b = DupContact(2, "same", "X Y", listOf("0470123456"))
        assertTrue(DuplicateFinder.find(listOf(a, b), "BE").isEmpty())
    }

    @Test
    fun mergeRecordCoversAContactOnlyWhenItJoinedTwoOfItsParts() {
        val r = MergeRecord("id", "Anna", listOf(listOf(1L, 2L), listOf(3L), listOf(4L)), 0)
        assertTrue(r.covers(listOf(1L, 3L)))
        assertTrue(r.covers(listOf(2L, 4L, 9L)))
        assertFalse(r.covers(listOf(1L, 2L)))
        assertFalse(r.covers(listOf(9L)))
    }
}
