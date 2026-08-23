package com.kg.museumly

/**
 * Phase 0 spike data: hardcoded, no network layer, no Room, no repository.
 * Real Met Museum records (title/artist/date/primaryImageSmall/measurements)
 * fetched live from collectionapi.metmuseum.org and frozen here.
 *
 * AIC's image CDN (www.artic.edu/iiif/...) was tried first but sits behind
 * Cloudflare bot protection that 403s every non-browser client (curl, OkHttp,
 * Coil alike) — confirmed by curl from multiple UAs/IPs, while its own JSON
 * API (api.artic.edu) is unprotected. Met's images.metmuseum.org has no such
 * gate, so all 20 URLs below were verified (HTTP 200) before being frozen here.
 *
 * aspectRatio = physical width / height (cm, from `measurements`), used to
 * reserve the correct on-screen box before the image decodes. Met has no
 * IIIF-style resizable URLs, so `primaryImageSmall` (web-large, ~100-300KB)
 * is used instead of `primaryImage` (original, several MB — too heavy to
 * decode smoothly while flinging through a pager).
 */
data class Artwork(
    val id: String,
    val title: String,
    val artist: String,
    val year: String,
    val imageUrl: String,
    val aspectRatio: Float,
)

val sampleArtworks: List<Artwork> = listOf(
    Artwork(
        id = "met:656430",
        title = "Portrait of Yun Dongseom (1710–1795)",
        artist = "Unidentified artist",
        year = "ca. 1790–1805",
        imageUrl = "https://images.metmuseum.org/CRDImages/as/web-large/DP341229.jpg",
        aspectRatio = 78.74016f / 193.04039f,
    ),
    Artwork(
        id = "met:50486",
        title = "Bamboo in the Wind",
        artist = "Yi Jeong (artist name: Taneun)",
        year = "early 17th century",
        imageUrl = "https://images.metmuseum.org/CRDImages/as/web-large/DP355790.jpg",
        aspectRatio = 53.3401f / 115.5702f,
    ),
    Artwork(
        id = "met:74813",
        title = "Shakyamuni Triad",
        artist = "Unidentified artist",
        year = "1565",
        imageUrl = "https://images.metmuseum.org/CRDImages/as/web-large/DP355788.jpg",
        aspectRatio = 32f / 60.5f,
    ),
    Artwork(
        id = "met:436573",
        title = "Cardinal Fernando Niño de Guevara (1541–1609)",
        artist = "El Greco (Domenikos Theotokopoulos)",
        year = "ca. 1600",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP-17777-001.jpg",
        aspectRatio = 108f / 170.8f,
    ),
    Artwork(
        id = "met:451725",
        title = "The Concourse of the Birds",
        artist = "Habiballah of Sava",
        year = "ca. 1600",
        imageUrl = "https://images.metmuseum.org/CRDImages/is/web-large/DP234083.jpg",
        aspectRatio = 20.7963f / 33.0201f,
    ),
    Artwork(
        id = "met:436851",
        title = "Elizabeth Farren, Later Countess of Derby",
        artist = "Sir Thomas Lawrence",
        year = "1790",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP169218.jpg",
        aspectRatio = 146.1f / 238.8f,
    ),
    Artwork(
        id = "met:437447",
        title = "Captain George K. H. Coussmaker (1759–1801)",
        artist = "Sir Joshua Reynolds",
        year = "1782",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP169215.jpg",
        aspectRatio = 145.4f / 238.1f,
    ),
    Artwork(
        id = "met:453336",
        title = "A Stallion",
        artist = "Habiballah of Sava",
        year = "ca. 1601–6",
        imageUrl = "https://images.metmuseum.org/CRDImages/is/web-large/DP234078.jpg",
        aspectRatio = 20.3f / 30.1f,
    ),
    Artwork(
        id = "met:435728",
        title = "The Last Communion of Saint Jerome",
        artist = "Botticelli (Alessandro di Mariano Filipepi)",
        year = "early 1490s",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP-24049-001.jpg",
        aspectRatio = 25.4f / 34.3f,
    ),
    Artwork(
        id = "met:437609",
        title = "The Holy Family with the Young Saint John the Baptist",
        artist = "Andrea del Sarto",
        year = "ca. 1528",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP295025.jpg",
        aspectRatio = 100.6f / 135.9f,
    ),
    Artwork(
        id = "met:437891",
        title = "Mars and Venus United by Love",
        artist = "Paolo Veronese",
        year = "1570s",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP167124.jpg",
        aspectRatio = 161f / 205.7f,
    ),
    Artwork(
        id = "met:435641",
        title = "Madonna and Child",
        artist = "Giovanni Bellini",
        year = "late 1480s",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DT375.jpg",
        aspectRatio = 71.1f / 88.9f,
    ),
    Artwork(
        id = "met:436323",
        title = "Marie Emilie Coignet de Courson with a Dog",
        artist = "Jean Honoré Fragonard",
        year = "ca. 1769",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP-1019-01.jpg",
        aspectRatio = 65.4f / 81.3f,
    ),
    Artwork(
        id = "met:436244",
        title = "Virgin and Child with Saint Anne",
        artist = "Albrecht Dürer",
        year = "probably 1519",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP280846.jpg",
        aspectRatio = 49.8f / 60f,
    ),
    Artwork(
        id = "met:438814",
        title = "The Abduction of Rebecca",
        artist = "Eugène Delacroix",
        year = "1846",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP-14344-001.jpg",
        aspectRatio = 121.28524f / 139.70029f,
    ),
    Artwork(
        id = "met:435851",
        title = "The Meditation on the Passion",
        artist = "Vittore Carpaccio",
        year = "ca. 1490",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP296427.jpg",
        aspectRatio = 86.7f / 70.5f,
    ),
    Artwork(
        id = "met:437826",
        title = "Venus and Adonis",
        artist = "Titian (Tiziano Vecellio)",
        year = "1550s",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP-19299-001.jpg",
        aspectRatio = 133.4f / 106.7f,
    ),
    Artwork(
        id = "met:437769",
        title = "Clothing the Naked",
        artist = "Michiel Sweerts",
        year = "ca. 1661",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP-15762-001.jpg",
        aspectRatio = 114.3f / 81.9f,
    ),
    Artwork(
        id = "met:437329",
        title = "The Abduction of the Sabine Women",
        artist = "Nicolas Poussin",
        year = "probably 1633–34",
        imageUrl = "https://images.metmuseum.org/CRDImages/ep/web-large/DP-29324-001.jpg",
        aspectRatio = 209.9f / 154.6f,
    ),
    Artwork(
        id = "met:910555",
        title = "The Thirty-Six Poetic Immortals",
        artist = "Studio of Kano Takanobu",
        year = "early 17th century",
        imageUrl = "https://images.metmuseum.org/CRDImages/as/web-large/DP-35700-001.jpg",
        aspectRatio = 360.68073f / 165.41783f,
    ),
)
