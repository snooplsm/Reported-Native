package com.reported.nativeandroid.screens

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.reported.nativeandroid.app.SubmissionMedia
import com.reported.nativeandroid.app.VehicleDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "ReportedVehicleInfo"
private const val FEATURE_STATUS_TIMEOUT_SECONDS = 2L
private const val INFERENCE_TIMEOUT_SECONDS = 8L

internal object VehicleImageDescriptionEngine {
    suspend fun describeVehicle(context: Context, media: SubmissionMedia): VehicleDescription? =
        withContext(Dispatchers.IO) {
            if (media.isVideo || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return@withContext null
            }

            runCatching {
                val bitmap = decodeBitmap(context, Uri.parse(media.uri)) ?: return@withContext null
                try {
                    describeBitmap(context.applicationContext, bitmap)
                } finally {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }
                }
            }.onFailure { error ->
                Log.d(TAG, "Vehicle image description skipped", error)
            }.getOrNull()
        }

    private fun describeBitmap(context: Context, bitmap: Bitmap): VehicleDescription? {
        val options = ImageDescriberOptions.builder(context).build()
        val describer = ImageDescription.getClient(options)
        return try {
            val featureStatus = describer
                .checkFeatureStatus()
                .get(FEATURE_STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (featureStatus == FeatureStatus.UNAVAILABLE) {
                Log.d(TAG, "Image description feature unavailable")
                return null
            }

            val request = ImageDescriptionRequest.builder(bitmap).build()
            val description = describer
                .runInference(request)
                .get(INFERENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .description
                .trim()
            if (description.isBlank()) {
                null
            } else {
                description.toVehicleDescription()
            }
        } finally {
            describer.close()
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? =
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE, "file" -> uri.path
                ?.let(::File)
                ?.takeIf { it.exists() }
                ?.inputStream()
                ?.use(BitmapFactory::decodeStream)
            else -> context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
}

private fun String.toVehicleDescription(): VehicleDescription {
    val normalized = lowercase(Locale.US)
    val color = findVehicleColor(normalized)
    val make = findVehicleMake(normalized)
    val model = make?.let { findVehicleModel(normalized, it) }

    return VehicleDescription(
        imageDescription = this,
        color = color,
        make = make,
        model = model
    )
}

private fun findVehicleColor(text: String): String? {
    val colorAliases = linkedMapOf(
        "black" to listOf("black"),
        "white" to listOf("white"),
        "gray" to listOf("gray", "grey", "silver"),
        "red" to listOf("red", "maroon", "burgundy"),
        "blue" to listOf("blue", "navy"),
        "green" to listOf("green"),
        "yellow" to listOf("yellow", "gold"),
        "orange" to listOf("orange"),
        "brown" to listOf("brown", "tan", "beige"),
        "purple" to listOf("purple")
    )
    return colorAliases.entries.firstOrNull { (_, aliases) ->
        aliases.any { alias -> text.containsWord(alias) }
    }?.key?.titleCase()
}

private fun findVehicleMake(text: String): String? =
    VehicleMakes.firstOrNull { make ->
        text.containsWord(make.lowercase(Locale.US))
    }

private fun findVehicleModel(text: String, make: String): String? {
    val models = VehicleModelsByMake[make].orEmpty()
    val directModel = models.firstOrNull { model -> text.containsWord(model.lowercase(Locale.US)) }
    if (directModel != null) return directModel

    val makeIndex = text.indexOf(make.lowercase(Locale.US))
    if (makeIndex < 0) return null
    val afterMake = text
        .substring(makeIndex + make.length)
        .replace(Regex("[^a-z0-9 -]"), " ")
        .trim()
    val words = afterMake.split(Regex("\\s+")).filter { it.isNotBlank() }
    val stopWords = setOf("car", "sedan", "suv", "truck", "van", "vehicle", "bus", "taxi", "with", "in", "on", "parked", "driving")
    val candidateWords = words.takeWhile { it !in stopWords }.take(2)
    return candidateWords
        .joinToString(" ")
        .takeIf { it.length >= 2 }
        ?.titleCase()
}

private fun String.containsWord(word: String): Boolean =
    Regex("(^|[^a-z0-9])${Regex.escape(word)}([^a-z0-9]|\$)", RegexOption.IGNORE_CASE).containsMatchIn(this)

private fun String.titleCase(): String =
    split(Regex("\\s+|-"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
            }
        }

private val VehicleMakes = listOf(
    "Acura",
    "Audi",
    "BMW",
    "Buick",
    "Cadillac",
    "Chevrolet",
    "Chrysler",
    "Dodge",
    "Fiat",
    "Ford",
    "Genesis",
    "GMC",
    "Honda",
    "Hyundai",
    "Infiniti",
    "Jaguar",
    "Jeep",
    "Kia",
    "Land Rover",
    "Lexus",
    "Lincoln",
    "Mazda",
    "Mercedes",
    "Mercedes-Benz",
    "Mini",
    "Mitsubishi",
    "Nissan",
    "Polestar",
    "Porsche",
    "Ram",
    "Rivian",
    "Subaru",
    "Tesla",
    "Toyota",
    "Volkswagen",
    "Volvo"
)

private val VehicleModelsByMake = mapOf(
    "BMW" to listOf("3 Series", "5 Series", "X1", "X3", "X5", "X7"),
    "Chevrolet" to listOf("Bolt", "Camaro", "Colorado", "Equinox", "Impala", "Malibu", "Silverado", "Suburban", "Tahoe", "Traverse"),
    "Dodge" to listOf("Challenger", "Charger", "Durango", "Grand Caravan"),
    "Ford" to listOf("Bronco", "Edge", "Escape", "Explorer", "F-150", "Focus", "Fusion", "Maverick", "Mustang", "Ranger", "Transit"),
    "GMC" to listOf("Acadia", "Canyon", "Savana", "Sierra", "Terrain", "Yukon"),
    "Honda" to listOf("Accord", "Civic", "CR-V", "Fit", "HR-V", "Odyssey", "Pilot", "Ridgeline"),
    "Hyundai" to listOf("Elantra", "Ioniq", "Kona", "Palisade", "Santa Fe", "Sonata", "Tucson"),
    "Jeep" to listOf("Cherokee", "Compass", "Gladiator", "Grand Cherokee", "Renegade", "Wrangler"),
    "Kia" to listOf("Forte", "K5", "Niro", "Optima", "Rio", "Seltos", "Sorento", "Soul", "Sportage", "Telluride"),
    "Lexus" to listOf("ES", "GX", "IS", "LS", "LX", "NX", "RX", "UX"),
    "Mazda" to listOf("CX-3", "CX-30", "CX-5", "CX-50", "CX-9", "Mazda3", "Mazda6", "Miata"),
    "Mercedes" to listOf("C-Class", "E-Class", "GLA", "GLC", "GLE", "S-Class", "Sprinter"),
    "Mercedes-Benz" to listOf("C-Class", "E-Class", "GLA", "GLC", "GLE", "S-Class", "Sprinter"),
    "Nissan" to listOf("Altima", "Frontier", "Leaf", "Maxima", "Murano", "Pathfinder", "Rogue", "Sentra", "Titan", "Versa"),
    "Subaru" to listOf("Ascent", "Crosstrek", "Forester", "Impreza", "Legacy", "Outback", "WRX"),
    "Tesla" to listOf("Model 3", "Model S", "Model X", "Model Y", "Cybertruck"),
    "Toyota" to listOf("4Runner", "Camry", "Corolla", "Highlander", "Prius", "RAV4", "Sienna", "Tacoma", "Tundra", "Venza"),
    "Volkswagen" to listOf("Atlas", "Beetle", "Golf", "ID.4", "Jetta", "Passat", "Taos", "Tiguan"),
    "Volvo" to listOf("S60", "S90", "V60", "XC40", "XC60", "XC90")
)
