package com.crsmthw.sheliak.data.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NormalizedKeysTest {

    @Test
    fun `normalize lower-cases strips diacritics and punctuation and collapses whitespace`() {
        assertEquals("bjork", NormalizedKeys.normalize("Björk"))
        assertEquals("sigur ros", NormalizedKeys.normalize("Sigur Rós"))
        assertEquals("acdc", NormalizedKeys.normalize("AC/DC"))
        assertEquals("guns n roses", NormalizedKeys.normalize("Guns N' Roses"))
        assertEquals("the band", NormalizedKeys.normalize("  The   Band  "))
        assertEquals("motorhead", NormalizedKeys.normalize("Motörhead"))
        assertEquals("beyonce", NormalizedKeys.normalize("BEYONCÉ"))
        assertEquals("pnk", NormalizedKeys.normalize("P!nk"))
        assertEquals("", NormalizedKeys.normalize(null))
        assertEquals("", NormalizedKeys.normalize("?!…"))
    }

    @Test
    fun `decomposed and composed input give the same key`() {
        assertEquals(NormalizedKeys.normalize("Beyoncé"), NormalizedKeys.normalize("Beyoncé"))
    }

    @Test
    fun `Greek and Cyrillic accents are stripped`() {
        assertEquals("αθηνα", NormalizedKeys.normalize("Αθηνά"))
        assertEquals("иван", NormalizedKeys.normalize("Иван"))
        assertEquals("ежик", NormalizedKeys.normalize("Ёжик"))
    }

    @Test
    fun `non-Latin scripts survive intact`() {
        assertEquals("坂本龍一", NormalizedKeys.normalize("坂本龍一"))
        assertEquals("방탄소년단", NormalizedKeys.normalize("방탄소년단"))   // NFD splits Hangul; it must come back whole
        assertEquals("ए आर रहमान", NormalizedKeys.normalize("ए. आर. रहमान"))   // vowel signs are part of the letters
        assertEquals("عمرو دياب", NormalizedKeys.normalize("عمرو دياب"))
    }

    @Test
    fun `a Japanese dakuten is part of the letter, not an accent`() {
        assertNotEquals(NormalizedKeys.normalize("がっこう"), NormalizedKeys.normalize("かっこう"))
    }

    @Test
    fun `album keys keep artist and title apart`() {
        assertNotEquals(NormalizedKeys.albumKey("ab", "c"), NormalizedKeys.albumKey("a", "bc"))
        assertNotEquals(NormalizedKeys.albumKey("Queen", "Greatest Hits"), NormalizedKeys.albumKey("ABBA", "Greatest Hits"))
    }

    @Test
    fun `the same album tagged slightly differently on two sources gets one key`() {
        assertEquals(NormalizedKeys.albumKey("Sigur Rós", "Ágætis byrjun"), NormalizedKeys.albumKey("Sigur Ros", "Agætis Byrjun"))
        assertEquals(NormalizedKeys.albumKey("AC/DC", "Back in Black"), NormalizedKeys.albumKey("ACDC", "Back In Black!"))
    }

    @Test
    fun `track keys include disc and track numbers`() {
        val a = NormalizedKeys.trackKey("Pink Floyd", "The Wall", 1, 1, "In the Flesh?")
        val b = NormalizedKeys.trackKey("Pink Floyd", "The Wall", 2, 7, "In the Flesh")
        assertNotEquals(a, b)
        assertEquals(a, NormalizedKeys.trackKey("pink floyd", "the wall", 1, 1, "In The Flesh?"))
        assertEquals("||||", NormalizedKeys.trackKey(null, null, null, null, null))
        assertEquals("a||||t", NormalizedKeys.trackKey("A", null, null, null, "T"))
    }

    @Test
    fun `sort keys drop leading punctuation and keep the rest`() {
        assertEquals("til tuesday", NormalizedKeys.sortKey("'Til Tuesday"))
        assertEquals("what's the story) morning glory?", NormalizedKeys.sortKey("(What's the Story) Morning Glory?"))
        assertEquals("elan", NormalizedKeys.sortKey("Élan"))
        assertEquals("abc def", NormalizedKeys.sortKey("  ABC   def "))
        assertEquals("!!!", NormalizedKeys.sortKey("!!!"))
        assertEquals("", NormalizedKeys.sortKey("   "))
        assertEquals("", NormalizedKeys.sortKey(null))
    }

    @Test
    fun `sort keys order accented titles beside their plain spelling`() {
        val sorted = listOf("Zebra", "Élan", "Eagle", "apple").sortedBy(NormalizedKeys::sortKey)
        assertEquals(listOf("apple", "Eagle", "Élan", "Zebra"), sorted)
    }
}
