package com.reported.nativeandroid.screens

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.reported.nativeandroid.BuildConfig
import com.reported.nativeandroid.app.AddressSuggestion
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.shared.model.CityReportingRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ExtractedSubmissionMetadata(
    val occurredAtIso: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val videoThumbnail: Bitmap? = null
)

private const val NYC_SEARCH_URL = "https://geosearch.planninglabs.nyc/v2/search"
private const val NYC_REVERSE_URL = "https://geosearch.planninglabs.nyc/v2/reverse"
private const val PHILADELPHIA_AIS_BASE_URL = "https://api.phila.gov/ais/v1"

enum class ReportAddressProvider(
    val id: String,
    val searchLabel: String,
    val searchingLabel: String,
    val noMatchesLabel: String,
    val reverseLookupLabel: String
) {
    NewYorkCity(
        id = CityReportingRules.NYC_ADDRESS_PROVIDER_ID,
        searchLabel = "Search NYC address",
        searchingLabel = "Searching NYC addresses",
        noMatchesLabel = "No NYC address matches found.",
        reverseLookupLabel = "Finding NYC address"
    ),
    Philadelphia(
        id = CityReportingRules.PHILADELPHIA_ADDRESS_PROVIDER_ID,
        searchLabel = "Search Philadelphia address",
        searchingLabel = "Searching Philadelphia addresses",
        noMatchesLabel = "No Philadelphia address matches found.",
        reverseLookupLabel = "Finding Philadelphia address"
    );
}

fun reportAddressProviderFor(
    latitude: Double?,
    longitude: Double?,
    address: String
): ReportAddressProvider =
    if (CityReportingRules.isPhiladelphiaReport(latitude, longitude, address)) {
        ReportAddressProvider.Philadelphia
    } else {
        ReportAddressProvider.NewYorkCity
    }

fun reportAddressProviderFor(
    context: Context,
    latitude: Double?,
    longitude: Double?,
    address: String
): ReportAddressProvider {
    if (latitude != null && longitude != null) {
        if (CityBoundaryIndex.contains(context, CityReportingRules.PHILADELPHIA_CITY_ID, latitude, longitude)) {
            return ReportAddressProvider.Philadelphia
        }
        if (CityBoundaryIndex.contains(context, CityReportingRules.NYC_CITY_ID, latitude, longitude)) {
            return ReportAddressProvider.NewYorkCity
        }
    }
    return reportAddressProviderFor(latitude, longitude, address)
}

suspend fun buildSubmissionMedia(
    context: Context,
    uri: Uri,
    isVideo: Boolean
): SubmissionMedia = withContext(Dispatchers.IO) {
    val durableUri = persistSubmissionMedia(context, uri, isVideo)
    val mimeType = if (isVideo) {
        context.contentResolver.getType(uri).orEmpty().ifBlank { "video/*" }
    } else {
        "image/jpeg"
    }
    SubmissionMedia(
        uri = durableUri.toString(),
        displayName = displayNameForUri(context, uri),
        mimeType = mimeType,
        isVideo = isVideo,
        sourceUri = uri.toString()
    )
}

suspend fun extractSubmissionMetadata(
    context: Context,
    media: SubmissionMedia
): ExtractedSubmissionMetadata = withContext(Dispatchers.IO) {
    val durableUri = Uri.parse(media.uri)
    val sourceUri = media.sourceUri?.let(Uri::parse)
    if (media.isVideo) {
        extractVideoMetadata(context, sourceUri ?: durableUri)
    } else {
        extractImageMetadata(context, sourceUri, durableUri)
    }
}

suspend fun reverseGeocodeNyc(latitude: Double, longitude: Double): AddressSuggestion? =
    withContext(Dispatchers.IO) {
        val url = URL("$NYC_REVERSE_URL?point.lat=$latitude&point.lon=$longitude&size=1")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        runCatching {
            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val feature = json.optJSONArray("features")?.optJSONObject(0) ?: return@use null
                featureToSuggestion(feature)
            }
        }.getOrNull().also {
            connection.disconnect()
        }
    }

suspend fun reverseGeocodeAddress(context: Context, latitude: Double, longitude: Double): AddressSuggestion? =
    when (reportAddressProviderFor(context, latitude, longitude, "")) {
        ReportAddressProvider.Philadelphia -> reverseGeocodePhiladelphiaAis(latitude, longitude)
            ?: reverseGeocodeNyc(latitude, longitude)
        ReportAddressProvider.NewYorkCity -> reverseGeocodeNyc(latitude, longitude)
    }
        ?: withContext(Dispatchers.IO) { reverseGeocodePlatform(context, latitude, longitude) }

suspend fun searchReportAddresses(
    context: Context,
    query: String,
    latitude: Double?,
    longitude: Double?,
    address: String
): List<AddressSuggestion> =
    when (reportAddressProviderFor(context, latitude, longitude, address.ifBlank { query })) {
        ReportAddressProvider.Philadelphia -> searchPhiladelphiaAisAddresses(query)
        ReportAddressProvider.NewYorkCity -> searchNycAddresses(query)
    }

suspend fun searchNycAddresses(query: String): List<AddressSuggestion> = withContext(Dispatchers.IO) {
    if (query.isBlank()) {
        return@withContext emptyList()
    }
    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
    val url = URL("$NYC_SEARCH_URL?text=$encoded")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 5_000
        readTimeout = 5_000
    }
    runCatching {
        connection.inputStream.bufferedReader().use { reader ->
            val json = JSONObject(reader.readText())
            val features = json.optJSONArray("features") ?: return@use emptyList()
            buildList {
                for (index in 0 until features.length()) {
                    val feature = features.optJSONObject(index) ?: continue
                    featureToSuggestion(feature)?.let(::add)
                }
            }
        }
    }.getOrDefault(emptyList()).also {
        connection.disconnect()
    }
}

private suspend fun searchPhiladelphiaAisAddresses(query: String): List<AddressSuggestion> = withContext(Dispatchers.IO) {
    if (query.isBlank()) {
        return@withContext emptyList()
    }
    val encoded = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
    val json = fetchPhiladelphiaAisJson("$PHILADELPHIA_AIS_BASE_URL/search/$encoded") ?: return@withContext emptyList()
    val features = json.optJSONArray("features") ?: return@withContext emptyList()
    buildList {
        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index) ?: continue
            philadelphiaAisFeatureToSuggestion(feature)?.let(::add)
        }
    }
}

private suspend fun reverseGeocodePhiladelphiaAis(latitude: Double, longitude: Double): AddressSuggestion? =
    withContext(Dispatchers.IO) {
        val json = fetchPhiladelphiaAisJson(
            "$PHILADELPHIA_AIS_BASE_URL/reverse_geocode/$longitude,$latitude?srid=4326&search_radius=500"
        ) ?: return@withContext null
        val feature = json.optJSONArray("features")?.optJSONObject(0) ?: return@withContext null
        philadelphiaAisFeatureToSuggestion(feature, fallbackLatitude = latitude, fallbackLongitude = longitude)
    }

private fun fetchPhiladelphiaAisJson(url: String): JSONObject? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 5_000
        readTimeout = 5_000
        BuildConfig.PHILADELPHIA_AIS_GATEKEEPER_KEY
            .takeIf { it.isNotBlank() }
            ?.let { setRequestProperty("Authorization", "Gatekeeper-Key $it") }
    }
    return runCatching {
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader().use { reader ->
            JSONObject(reader.readText())
        }
    }.getOrNull().also {
        connection.disconnect()
    }
}

private fun featureToSuggestion(feature: JSONObject): AddressSuggestion? {
    val properties = feature.optJSONObject("properties") ?: return null
    val geometry = feature.optJSONObject("geometry") ?: return null
    val coordinates = geometry.optJSONArray("coordinates") ?: return null
    return AddressSuggestion(
        label = properties.optString("label"),
        latitude = coordinates.optDouble(1),
        longitude = coordinates.optDouble(0),
        region = "NY"
    )
}

private fun philadelphiaAisFeatureToSuggestion(
    feature: JSONObject,
    fallbackLatitude: Double? = null,
    fallbackLongitude: Double? = null
): AddressSuggestion? {
    val properties = feature.optJSONObject("properties") ?: return null
    val geometry = feature.optJSONObject("geometry")
    val coordinates = geometry?.optJSONArray("coordinates")
    val longitude = coordinates?.optDouble(0)?.takeIf { it.isFinite() } ?: fallbackLongitude ?: return null
    val latitude = coordinates?.optDouble(1)?.takeIf { it.isFinite() } ?: fallbackLatitude ?: return null
    val streetAddress = properties.optString("street_address")
        .ifBlank { properties.optString("address") }
        .ifBlank { properties.optString("opa_address") }
    val zipCode = properties.optString("zip_code").takeIf { it.isNotBlank() }
    val label = buildString {
        append(streetAddress.ifBlank { "Philadelphia address" })
        if (!contains("Philadelphia", ignoreCase = true)) append(", Philadelphia")
        if (!contains(", PA", ignoreCase = true)) append(", PA")
        if (!zipCode.isNullOrBlank() && !contains(zipCode)) append(" $zipCode")
    }
    return AddressSuggestion(
        label = label,
        latitude = latitude,
        longitude = longitude,
        region = "PA",
        providerId = CityReportingRules.PHILADELPHIA_ADDRESS_PROVIDER_ID,
        blockNumber = properties.optString("address_low").takeIf { it.isNotBlank() }
            ?: streetAddress.firstStreetNumber(),
        streetName = properties.optString("street_full").takeIf { it.isNotBlank() },
        zipCode = zipCode
    )
}

private fun String.firstStreetNumber(): String? =
    Regex("""^\s*(\d+[A-Za-z]?)""").find(this)?.groupValues?.getOrNull(1)

private object CityBoundaryIndex {
    private val cache = mutableMapOf<String, CityBoundary?>()

    fun contains(context: Context, cityId: String, latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        val boundary = cache.getOrPut(cityId) {
            load(context, cityId)
        } ?: return false
        return boundary.contains(longitude, latitude)
    }

    private fun load(context: Context, cityId: String): CityBoundary? =
        runCatching {
            val fileName = when (cityId) {
                CityReportingRules.PHILADELPHIA_CITY_ID -> "city-boundaries/philadelphia.geojson"
                CityReportingRules.NYC_CITY_ID -> "city-boundaries/nyc.geojson"
                else -> return null
            }
            val json = context.assets.open(fileName).bufferedReader().use { reader ->
                JSONObject(reader.readText())
            }
            val bboxArray = json.optJSONObject("properties")?.optJSONArray("bbox")
            val bbox = if (bboxArray != null && bboxArray.length() >= 4) {
                DoubleArray(4) { index -> bboxArray.optDouble(index) }
            } else {
                null
            }
            val features = json.optJSONArray("features") ?: return null
            val polygons = buildList {
                for (featureIndex in 0 until features.length()) {
                    val geometry = features.optJSONObject(featureIndex)
                        ?.optJSONObject("geometry")
                        ?: continue
                    addAll(parseBoundaryGeometry(geometry))
                }
            }
            CityBoundary(bbox = bbox, polygons = polygons)
        }.getOrNull()
}

private data class CityBoundary(
    val bbox: DoubleArray?,
    val polygons: List<List<List<BoundaryPoint>>>
) {
    fun contains(longitude: Double, latitude: Double): Boolean {
        val bounds = bbox
        if (bounds != null && (
                longitude < bounds[0] ||
                    latitude < bounds[1] ||
                    longitude > bounds[2] ||
                    latitude > bounds[3]
                )
        ) {
            return false
        }
        return polygons.any { polygonContains(it, longitude, latitude) }
    }
}

private data class BoundaryPoint(val longitude: Double, val latitude: Double)

private fun parseBoundaryGeometry(geometry: JSONObject): List<List<List<BoundaryPoint>>> {
    val type = geometry.optString("type")
    val coordinates = geometry.optJSONArray("coordinates") ?: return emptyList()
    return when (type) {
        "Polygon" -> listOf(parseBoundaryPolygon(coordinates))
        "MultiPolygon" -> buildList {
            for (index in 0 until coordinates.length()) {
                coordinates.optJSONArray(index)?.let { add(parseBoundaryPolygon(it)) }
            }
        }
        else -> emptyList()
    }
}

private fun parseBoundaryPolygon(polygon: org.json.JSONArray): List<List<BoundaryPoint>> =
    buildList {
        for (ringIndex in 0 until polygon.length()) {
            val ring = polygon.optJSONArray(ringIndex) ?: continue
            add(
                buildList {
                    for (pointIndex in 0 until ring.length()) {
                        val point = ring.optJSONArray(pointIndex) ?: continue
                        add(BoundaryPoint(point.optDouble(0), point.optDouble(1)))
                    }
                }
            )
        }
    }

private fun polygonContains(
    polygon: List<List<BoundaryPoint>>,
    longitude: Double,
    latitude: Double
): Boolean {
    val outer = polygon.firstOrNull() ?: return false
    if (!ringContains(outer, longitude, latitude)) return false
    return polygon.drop(1).none { hole -> ringContains(hole, longitude, latitude) }
}

private fun ringContains(
    ring: List<BoundaryPoint>,
    longitude: Double,
    latitude: Double
): Boolean {
    if (ring.size < 3) return false
    var inside = false
    var previous = ring.last()
    ring.forEach { current ->
        val intersects = (current.latitude > latitude) != (previous.latitude > latitude) &&
            longitude < (previous.longitude - current.longitude) *
            (latitude - current.latitude) /
            (previous.latitude - current.latitude) +
            current.longitude
        if (intersects) inside = !inside
        previous = current
    }
    return inside
}

@Suppress("DEPRECATION")
private fun reverseGeocodePlatform(context: Context, latitude: Double, longitude: Double): AddressSuggestion? {
    val geocoder = Geocoder(context, Locale.US)
    val address = runCatching {
        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
    }.getOrNull() ?: return null
    val label = address.getAddressLine(0)
        ?: listOfNotNull(
            address.thoroughfare,
            address.locality,
            address.adminArea,
            address.countryName
        ).joinToString(", ")
    if (label.isBlank()) return null
    return AddressSuggestion(
        label = label,
        latitude = latitude,
        longitude = longitude,
        region = normalizeUsState(address.adminArea)
    )
}

private fun normalizeUsState(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val value = raw.trim()
    if (value.length == 2) return value.uppercase(Locale.US)
    return usStateAbbreviations[value.lowercase(Locale.US)]
}

private val usStateAbbreviations = mapOf(
    "alabama" to "AL",
    "alaska" to "AK",
    "arizona" to "AZ",
    "arkansas" to "AR",
    "california" to "CA",
    "colorado" to "CO",
    "connecticut" to "CT",
    "delaware" to "DE",
    "florida" to "FL",
    "georgia" to "GA",
    "hawaii" to "HI",
    "idaho" to "ID",
    "illinois" to "IL",
    "indiana" to "IN",
    "iowa" to "IA",
    "kansas" to "KS",
    "kentucky" to "KY",
    "louisiana" to "LA",
    "maine" to "ME",
    "maryland" to "MD",
    "massachusetts" to "MA",
    "michigan" to "MI",
    "minnesota" to "MN",
    "mississippi" to "MS",
    "missouri" to "MO",
    "montana" to "MT",
    "nebraska" to "NE",
    "nevada" to "NV",
    "new hampshire" to "NH",
    "new jersey" to "NJ",
    "new mexico" to "NM",
    "new york" to "NY",
    "north carolina" to "NC",
    "north dakota" to "ND",
    "ohio" to "OH",
    "oklahoma" to "OK",
    "oregon" to "OR",
    "pennsylvania" to "PA",
    "rhode island" to "RI",
    "south carolina" to "SC",
    "south dakota" to "SD",
    "tennessee" to "TN",
    "texas" to "TX",
    "utah" to "UT",
    "vermont" to "VT",
    "virginia" to "VA",
    "washington" to "WA",
    "west virginia" to "WV",
    "wisconsin" to "WI",
    "wyoming" to "WY",
    "district of columbia" to "DC"
)

private fun extractImageMetadata(
    context: Context,
    sourceUri: Uri?,
    durableUri: Uri
): ExtractedSubmissionMetadata {
    val candidates = buildList {
        if (sourceUri != null) {
            val mediaStoreUri = mediaStoreUriForPickerOrDocument(context, sourceUri)
            if (mediaStoreUri != null) {
                originalMediaUri(mediaStoreUri)?.let(::add)
                add(mediaStoreUri)
            }
            if (!sourceUri.isPickerUri()) {
                originalMediaUri(sourceUri)?.let(::add)
            }
            add(sourceUri)
        }
        add(durableUri)
    }.distinctBy(Uri::toString)

    var best = ExtractedSubmissionMetadata()
    candidates.forEach { uri ->
        val exifMetadata = extractImageExifMetadata(context, uri)
        best = best.withFallback(exifMetadata)
        val mediaStoreDate = queryMediaDateTaken(context, uri)
        if (best.occurredAtIso == null && mediaStoreDate != null) {
            best = best.copy(occurredAtIso = mediaStoreDate)
        }
        if (best.occurredAtIso != null && best.latitude != null && best.longitude != null) {
            return best
        }
    }
    return best
}

private fun extractImageExifMetadata(context: Context, uri: Uri): ExtractedSubmissionMetadata {
    val input = openInputStreamSafely(context, uri) ?: return ExtractedSubmissionMetadata()
    return runCatching {
        input.use {
            val exif = ExifInterface(it)
            val latLong = exif.latLong
            ExtractedSubmissionMetadata(
                occurredAtIso = readExifDateTime(exif),
                latitude = latLong?.getOrNull(0),
                longitude = latLong?.getOrNull(1)
            )
        }
    }.getOrDefault(ExtractedSubmissionMetadata())
}

private fun extractVideoMetadata(context: Context, uri: Uri): ExtractedSubmissionMetadata {
    val retriever = MediaMetadataRetriever()
    return try {
        setRetrieverDataSource(context, retriever, uri)
        val date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
        val location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
        val thumbnail = retriever.getFrameAtTime(0)
        val (lat, lon) = parseIso6709Location(location)
        ExtractedSubmissionMetadata(
            occurredAtIso = date?.let(::parseVideoDate),
            latitude = lat,
            longitude = lon,
            videoThumbnail = thumbnail
        )
    } finally {
        retriever.release()
    }
}

private fun ExtractedSubmissionMetadata.withFallback(other: ExtractedSubmissionMetadata): ExtractedSubmissionMetadata =
    ExtractedSubmissionMetadata(
        occurredAtIso = occurredAtIso ?: other.occurredAtIso,
        latitude = latitude ?: other.latitude,
        longitude = longitude ?: other.longitude,
        videoThumbnail = videoThumbnail ?: other.videoThumbnail
    )

private fun originalMediaUri(uri: Uri): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { MediaStore.setRequireOriginal(uri) }.getOrNull()
    } else {
        null
    }

private fun mediaStoreUriForPickerOrDocument(context: Context, uri: Uri): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == ContentResolver.SCHEME_CONTENT) {
        runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
    } else {
        null
    }

private fun Uri.isPickerUri(): Boolean =
    authority == "media" && pathSegments.firstOrNull() == "picker"

private fun readExifDateTime(exif: ExifInterface): String? {
    val candidates = listOf(
        ExifInterface.TAG_DATETIME_ORIGINAL to ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED to ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_DATETIME to ExifInterface.TAG_OFFSET_TIME
    )
    candidates.forEach { (dateTag, offsetTag) ->
        val rawDate = exif.getAttribute(dateTag)
        val rawOffset = exif.getAttribute(offsetTag)
        parseExifDateTime(rawDate, rawOffset)?.let { return it }
    }
    return parseExifGpsDateTime(
        dateStamp = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP),
        timeStamp = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP)
    )
}

private fun parseExifDateTime(raw: String?, offset: String? = null): String? = runCatching {
    if (raw.isNullOrBlank()) return@runCatching null
    val formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    val localDateTime = LocalDateTime.parse(raw, formatter)
    val zoneOffset = offset?.takeIf { it.matches(Regex("[+-]\\d{2}:?\\d{2}")) }
        ?.let { normalized ->
            ZoneOffset.of(
                if (normalized.length == 5) {
                    normalized
                } else {
                    "${normalized.substring(0, 3)}:${normalized.substring(3)}"
                }
            )
        }
    if (zoneOffset != null) {
        localDateTime.atOffset(zoneOffset).toInstant().toString()
    } else {
        localDateTime.atZone(ZoneId.systemDefault()).toInstant().toString()
    }
}.getOrNull()

private fun parseExifGpsDateTime(dateStamp: String?, timeStamp: String?): String? = runCatching {
    if (dateStamp.isNullOrBlank() || timeStamp.isNullOrBlank()) return@runCatching null
    val raw = "${dateStamp.trim()} ${timeStamp.trim()}"
    val formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    LocalDateTime.parse(raw, formatter).atOffset(ZoneOffset.UTC).toInstant().toString()
}.getOrNull()

private fun queryMediaDateTaken(context: Context, uri: Uri): String? {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
    val projection = arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED)
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val dateTakenIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val dateTaken = if (dateTakenIndex >= 0) cursor.getLong(dateTakenIndex) else 0L
            if (dateTaken > 0L) {
                return@use Instant.ofEpochMilli(dateTaken).toString()
            }
            val dateModifiedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val dateModified = if (dateModifiedIndex >= 0) cursor.getLong(dateModifiedIndex) else 0L
            if (dateModified > 0L) Instant.ofEpochSecond(dateModified).toString() else null
        }
    }.getOrNull()
}

private fun parseVideoDate(raw: String): String? = runCatching {
    Instant.parse(raw).toString()
}.getOrElse {
    runCatching {
        val normalized = raw.removeSuffix("Z")
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS")
        LocalDateTime.parse(normalized, formatter).atOffset(ZoneOffset.UTC).toInstant().toString()
    }.getOrNull()
}

private fun parseIso6709Location(raw: String?): Pair<Double?, Double?> {
    if (raw.isNullOrBlank()) return null to null
    val match = Regex("([+-][0-9.]+)([+-][0-9.]+)").find(raw) ?: return null to null
    return match.groupValues.getOrNull(1)?.toDoubleOrNull() to
        match.groupValues.getOrNull(2)?.toDoubleOrNull()
}

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) {
            return cursor.getString(nameIndex) ?: "Upload"
        }
    }
    return uri.lastPathSegment ?: "Upload"
}

private fun displayNameForUri(context: Context, uri: Uri): String =
    if (uri.scheme == "file") {
        uri.path?.let(::File)?.name ?: uri.lastPathSegment ?: "Upload"
    } else {
        queryDisplayName(context.contentResolver, uri)
    }

private fun persistSubmissionMedia(context: Context, sourceUri: Uri, isVideo: Boolean): Uri {
    if (sourceUri.scheme == "file" && isVideo) {
        return sourceUri
    }

    val displayName = displayNameForUri(context, sourceUri)
    val extension = if (isVideo) {
        displayName.substringAfterLast('.', "").ifBlank {
            "mp4"
        }
    } else {
        "jpg"
    }
    val sanitizedBaseName = displayName.substringBeforeLast('.')
        .ifBlank { if (isVideo) "video" else "image" }
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
    val mediaDir = File(context.filesDir, "submission-media").apply { mkdirs() }
    val targetFile = File(mediaDir, "${System.currentTimeMillis()}_${sanitizedBaseName}.$extension")
    val persisted = if (isVideo) {
        copyOriginalMediaFile(context, sourceUri, targetFile)
    } else {
        copyNormalizedImageFile(context, sourceUri, targetFile)
    }
    return if (persisted) Uri.fromFile(targetFile) else sourceUri
}

private fun copyOriginalMediaFile(context: Context, sourceUri: Uri, targetFile: File): Boolean {
    openInputStreamSafely(context, sourceUri)?.use { input ->
        targetFile.outputStream().use { output ->
            input.copyTo(output)
        }
        return true
    }
    return false
}

private fun copyNormalizedImageFile(context: Context, sourceUri: Uri, targetFile: File): Boolean {
    val orientation = readExifOrientation(context, sourceUri)
    val sourceBitmap = openInputStreamSafely(context, sourceUri)?.use(BitmapFactory::decodeStream)
        ?: return copyOriginalMediaFile(context, sourceUri, targetFile)
    val normalizedBitmap = sourceBitmap.normalizedForExifOrientation(orientation)
    val wroteImage = targetFile.outputStream().use { output ->
        normalizedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
    }
    if (normalizedBitmap !== sourceBitmap) {
        sourceBitmap.recycle()
    }
    normalizedBitmap.recycle()
    if (!wroteImage) {
        targetFile.delete()
        return false
    }
    copyExifMetadataWithNormalOrientation(context, sourceUri, targetFile)
    return true
}

private fun readExifOrientation(context: Context, uri: Uri): Int {
    val input = openInputStreamSafely(context, uri) ?: return ExifInterface.ORIENTATION_UNDEFINED
    return runCatching {
        input.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
        }
    }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
}

private fun Bitmap.normalizedForExifOrientation(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return this
    }
    return runCatching {
        Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }.getOrDefault(this)
}

private fun copyExifMetadataWithNormalOrientation(context: Context, sourceUri: Uri, targetFile: File) {
    val input = openInputStreamSafely(context, sourceUri) ?: return
    runCatching {
        input.use {
            val sourceExif = ExifInterface(it)
            val targetExif = ExifInterface(targetFile.absolutePath)
            exifTagsToCopy.forEach { tag ->
                sourceExif.getAttribute(tag)?.let { value ->
                    targetExif.setAttribute(tag, value)
                }
            }
            targetExif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            targetExif.saveAttributes()
        }
    }
}

@Suppress("DEPRECATION")
private val exifTagsToCopy = listOf(
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME,
    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME,
    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_PROCESSING_METHOD,
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_SOFTWARE,
    ExifInterface.TAG_ARTIST,
    ExifInterface.TAG_COPYRIGHT,
    ExifInterface.TAG_EXPOSURE_TIME,
    ExifInterface.TAG_F_NUMBER,
    ExifInterface.TAG_ISO_SPEED_RATINGS,
    ExifInterface.TAG_FOCAL_LENGTH,
    ExifInterface.TAG_FLASH,
    ExifInterface.TAG_WHITE_BALANCE
)

private fun openInputStreamSafely(context: Context, uri: Uri): InputStream? = runCatching {
    when (uri.scheme) {
        "file" -> uri.path?.let(::File)?.takeIf { it.exists() }?.inputStream()
        else -> context.contentResolver.openInputStream(uri)
    }
}.getOrNull()

private fun setRetrieverDataSource(context: Context, retriever: MediaMetadataRetriever, uri: Uri) {
    if (uri.scheme == "file") {
        retriever.setDataSource(uri.path)
    } else {
        retriever.setDataSource(context, uri)
    }
}
