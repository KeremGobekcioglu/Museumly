package com.kg.museumly.model

/**
 * This class represents departments in a museum. This is shared between different api s so
 * that they can have same classification. I mean ui should not know about how departments differ
 * in api s, ui should see only one, unified logic.
 */
enum class Section(val id: String)
{
    EGYPT_NEAR_EAST("egypt_near_east"),
    GREEK_ROMAN("greek_roman"),
    ISLAMIC("islamic"),
    MEDIEVAL("medieval"),
    EUROPEAN("european"),
    ASIA("asia"),
    AFRICA_OCEANIA_AMERICAS("africa_oceania_americas")
}