package com.example.testcalendrierchateautheaud

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import org.json.JSONArray
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.io.File
import java.io.InputStream
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null

    // Connexion à Firebase Realtime Database
    private val database = FirebaseDatabase.getInstance().reference.child("adresses")

    private val coulesAdresses = mutableMapOf<String, String>()
    private var featureCollection: FeatureCollection? = null

    private var filtreCouleur = "TOUT"
    private var tourneeActuelle = "CHATEAU_THEBAUD_VIOLET"

    // Gestion de la permission de localisation
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            mapLibreMap?.style?.let { activerLocalisation(it) }
        } else {
            Toast.makeText(this, "Permission GPS refusée", Toast.LENGTH_SHORT).show()
        }
    }

    // --- RÈGLES TOURNÉE VIOLET CHÂTEAU-THÉBAUD ---
    private val reglesChateauThebaudViolet = mapOf(
        "Route du bignon" to "IMPAIR",
        "Impasse de la boisselerie" to "TOUS",
        "Rue de la forge" to "TOUS",
        "Chemin de la cigale" to "TOUS",
        "Chemin des prières" to "TOUS",
        "Rue du prineau" to "TOUS",
        "Route de montbert" to "TOUS",
        "Le Prineau" to "TOUS",
        "la chauviniere" to "TOUS",
        "le petit douet" to "TOUS",
        "Le Châtelier" to "TOUS",
        "Les Braudières" to "TOUS",
        "La Turmelière" to "TOUS",
        "La Pénissière" to "TOUS",
        "le pas breton" to "TOUS",
        "Les Saules" to "TOUS",
        "l’oumeau" to "TOUS",
        "La Gobarderie" to "TOUS",
        "Beauséjour" to "TOUS",
        "beaulaurier" to "TOUS",
        "La Barbotière" to "TOUS",
        "La Bourdinière" to "TOUS",
        "le moulin neuf" to "TOUS",
        "le grand moulin" to "TOUS",
        "route de la Poterie" to "TOUS",
        "les garennes" to "TOUS",
        "Les Aveneaux" to "TOUS",
        "Saint-Gabriel" to "TOUS",
        "Rue saint-thomas" to "IMPAIR",
        "Rue des landes" to "TOUS",
        "Allée des ajoncs" to "TOUS",
        "Allée des genets" to "TOUS",
        "Impasse des aubepines" to "TOUS",
        "Allée des bruyeres" to "TOUS",
        "Impasse des eglantiers" to "TOUS"
    )

    // --- RÈGLES TOURNÉE JAUNE CHÂTEAU-THÉBAUD ---
    private val reglesChateauThebaudJaune = mapOf(
        "Rue des Sport" to "TOUS"
    )

    // --- RÈGLES TOURNÉE VIOLET MAISDON ---
    private val reglesMaisdon = mapOf(
        "Rue de la Mairie" to "TOUS",
        "Rue de la Maine" to "TOUS",
        "Rue Beau Soleil" to "TOUS",
        "Rue des Garennes" to "TOUS",
        "Rue des Forges" to "IMPAIR",
        "Allée de Keelby" to "TOUS",
        "Rue du Fief de la Butte" to "TOUS",
        "Rue des Noëlles" to "TOUS",
        "Rue des Bouchauds" to "IMPAIR",
        "Chemin du Fief Beloin" to "TOUS",
        "Rue des Fromentaux" to "TOUS",
        "Rue de la Source" to "TOUS",
        "Avenue de Bonne Fontaine" to "TOUS",
        "Rue des Prés Verts" to "TOUS",
        "Rue Félix Dugast" to "PAIR",
        "Place de l'Eglise" to "TOUS"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        mapView = MapView(this).apply {
            onCreate(savedInstanceState)
        }

        setContent {
            var dialogData by remember { mutableStateOf<Pair<String, List<Pair<String, String>>>?>(null) }
            var menuTourneeOuvert by remember { mutableStateOf(false) }
            var nomTourneeAffiché by remember { mutableStateOf("Tournée Violet Ch.-Thébaud") }

            var menuFiltreOuvert by remember { mutableStateOf(false) }
            var afficherDialogueReinitialisation by remember { mutableStateOf(false) }

            var nbAFaire by remember { mutableStateOf(0) }
            var nbARepasser by remember { mutableStateOf(0) }
            var nbFaites by remember { mutableStateOf(0) }

            fun mettreAJourCompteurs() {
                var aFaire = 0
                var aRepasser = 0
                var faites = 0
                featureCollection?.features()?.forEach { feature ->
                    val id = feature.getStringProperty("id")
                    val couleur = coulesAdresses[id] ?: "red"
                    when (couleur) {
                        "red" -> aFaire++
                        "orange" -> aRepasser++
                        "green" -> faites++
                    }
                }
                nbAFaire = aFaire
                nbARepasser = aRepasser
                nbFaites = faites
            }

            // ÉCOUTEUR TEMPS RÉEL FIREBASE
            LaunchedEffect(Unit) {
                database.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        coulesAdresses.clear()
                        for (child in snapshot.children) {
                            val id = child.key ?: continue
                            val couleur = child.value as? String ?: "red"
                            coulesAdresses[id] = couleur
                        }
                        mapLibreMap?.style?.let { style ->
                            mettreAJourSource(style)
                        }
                        mettreAJourCompteurs()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e("Firebase", "Erreur de synchronisation : ${error.message}")
                    }
                })
            }

            val (couleurFondBouton, texteFiltre) = when (filtreCouleur) {
                "RED" -> Color(0xFFD32F2F) to "À faire"
                "ORANGE" -> Color(0xFFFFA500) to "À repasser"
                "GREEN" -> Color(0xFF2E7D32) to "Fait"
                "BLACK" -> Color(0xFF212121) to "Inhabité"
                "BLUE" -> Color(0xFF1976D2) to "SPV"
                else -> MaterialTheme.colorScheme.primary to "Tous"
            }

            val (couleurFondTournee, couleurTexteTournee) = when {
                tourneeActuelle.contains("JAUNE") -> Color(0xFFFBC02D) to Color.Black
                else -> Color(0xFF8E24AA) to Color.White
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = {
                        mapView.apply {
                            getMapAsync { map ->
                                mapLibreMap = map
                                map.cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
                                    .target(LatLng(47.1235, -1.4170))
                                    .zoom(13.0)
                                    .build()

                                val styleJson = """
                                {
                                  "version": 8,
                                  "sources": {
                                    "osm-tiles": {
                                      "type": "raster",
                                      "tiles": ["https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png"],
                                      "tileSize": 256,
                                      "attribution": "© OpenStreetMap contributors"
                                    }
                                  },
                                  "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
                                  "layers": [
                                    {
                                      "id": "osm-layer",
                                      "type": "raster",
                                      "source": "osm-tiles",
                                      "minzoom": 0,
                                      "maxzoom": 19
                                    }
                                  ]
                                }
                                """.trimIndent()

                                map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                                    val initialCollection = FeatureCollection.fromFeatures(emptyList())
                                    style.addSource(GeoJsonSource("adresses-source", initialCollection))

                                    val circleLayer = CircleLayer("adresses-pastilles-layer", "adresses-source")
                                    circleLayer.setProperties(
                                        circleRadius(14f),
                                        circleColor(
                                            match(
                                                get("couleur"),
                                                literal("orange"), color(android.graphics.Color.parseColor("#FFA500")),
                                                literal("green"), color(android.graphics.Color.GREEN),
                                                literal("black"), color(android.graphics.Color.BLACK),
                                                literal("blue"), color(android.graphics.Color.BLUE),
                                                color(android.graphics.Color.RED)
                                            )
                                        ),
                                        circleOpacity(0.8f)
                                    )
                                    style.addLayer(circleLayer)

                                    val symbolLayer = SymbolLayer("adresses-layer", "adresses-source")
                                    symbolLayer.setProperties(
                                        textField(get("numero")),
                                        textSize(12f),
                                        textColor("#FFFFFF"),
                                        textHaloColor("#000000"),
                                        textHaloWidth(1.2f),
                                        textAllowOverlap(true),
                                        textIgnorePlacement(true)
                                    )
                                    style.addLayer(symbolLayer)

                                    map.addOnMapClickListener { point ->
                                        val pixel = map.projection.toScreenLocation(point)
                                        val features = map.queryRenderedFeatures(pixel, "adresses-layer", "adresses-pastilles-layer")

                                        if (features.isNotEmpty()) {
                                            val clickedFeature = features[0]
                                            val numero = clickedFeature.getStringProperty("numero")
                                            val repClicked = if (clickedFeature.hasProperty("rep")) clickedFeature.getStringProperty("rep") else ""
                                            val rue = clickedFeature.getStringProperty("rue")

                                            val logementsMemeAdresse = featureCollection?.features()?.filter { f ->
                                                val fNum = f.getStringProperty("numero")
                                                val fRep = if (f.hasProperty("rep")) f.getStringProperty("rep") else ""
                                                val fRue = f.getStringProperty("rue")

                                                val numMatch = if (repClicked.isNotBlank() && numero.endsWith(repClicked)) {
                                                    "$fNum $fRep".trim() == numero.trim()
                                                } else {
                                                    fNum == numero && fRep == repClicked
                                                }
                                                numMatch && fRue == rue
                                            }?.map { f ->
                                                val id = f.getStringProperty("id")
                                                val rep = if (f.hasProperty("rep")) f.getStringProperty("rep") else ""
                                                val comp = if (f.hasProperty("complement")) f.getStringProperty("complement") else ""

                                                val intitule = when {
                                                    comp.isNotBlank() -> comp
                                                    rep.isNotBlank() -> "Logement $rep"
                                                    else -> "N°$numero"
                                                }
                                                Pair(id, intitule)
                                            } ?: emptyList()

                                            val titreAdresse = if (rue.isNotBlank()) "$numero $rue" else "N°$numero"
                                            dialogData = Pair(titreAdresse, logementsMemeAdresse)
                                            true
                                        } else {
                                            false
                                        }
                                    }

                                    rechargerCarte(style)
                                    mettreAJourCompteurs()
                                    verifierEtActiverLocalisation(style)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // MENU TOURNÉE ET COMPTEURS (Haut Gauche)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 80.dp, start = 16.dp)
                ) {
                    Button(
                        onClick = { menuTourneeOuvert = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = couleurFondTournee,
                            contentColor = couleurTexteTournee
                        )
                    ) {
                        Text(nomTourneeAffiché)
                    }

                    DropdownMenu(
                        expanded = menuTourneeOuvert,
                        onDismissRequest = { menuTourneeOuvert = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tournée Violet Château-Thébaud") },
                            onClick = {
                                tourneeActuelle = "CHATEAU_THEBAUD_VIOLET"
                                nomTourneeAffiché = "Tournée Violet Ch.-Thébaud"
                                menuTourneeOuvert = false
                                mapLibreMap?.style?.let { rechargerCarte(it) }
                                mettreAJourCompteurs()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Tournée Jaune Château-Thébaud") },
                            onClick = {
                                tourneeActuelle = "CHATEAU_THEBAUD_JAUNE"
                                nomTourneeAffiché = "Tournée Jaune Ch.-Thébaud"
                                menuTourneeOuvert = false
                                mapLibreMap?.style?.let { rechargerCarte(it) }
                                mettreAJourCompteurs()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Tournée Violet Maisdon") },
                            onClick = {
                                tourneeActuelle = "MAISDON"
                                nomTourneeAffiché = "Tournée Violet Maisdon"
                                menuTourneeOuvert = false
                                mapLibreMap?.style?.let { rechargerCarte(it) }
                                mettreAJourCompteurs()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // COMPTEURS (À faire, À repasser, Faits)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.widthIn(min = 160.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = "À faire : $nbAFaire",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "À repasser : $nbARepasser",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Faits : $nbFaites",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // MENU DÉROULANT FILTRE (Haut Droite)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 80.dp, end = 16.dp)
                ) {
                    Button(
                        onClick = { menuFiltreOuvert = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = couleurFondBouton,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = texteFiltre,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = menuFiltreOuvert,
                        onDismissRequest = { menuFiltreOuvert = false }
                    ) {
                        val options = listOf(
                            Triple("TOUT", "Tous", MaterialTheme.colorScheme.primary),
                            Triple("RED", "À faire", Color(0xFFD32F2F)),
                            Triple("ORANGE", "À repasser", Color(0xFFFFA500)),
                            Triple("GREEN", "Fait", Color(0xFF2E7D32)),
                            Triple("BLACK", "Inhabité", Color(0xFF212121)),
                            Triple("BLUE", "SPV", Color(0xFF1976D2))
                        )

                        options.forEach { (code, label, color) ->
                            DropdownMenuItem(
                                text = {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (filtreCouleur == code) color else color.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = label,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    filtreCouleur = code
                                    menuFiltreOuvert = false
                                    mapLibreMap?.style?.let { mettreAJourSource(it) }
                                }
                            )
                        }
                    }
                }

                // BOUTONS D'ACTION EN BAS À DROITE (Export + GPS + Réinit.)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 90.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    SmallFloatingActionButton(
                        onClick = { exporterResultatsCSV() },
                        containerColor = Color(0xFF388E3C),
                        contentColor = Color.White,
                        modifier = Modifier
                            .height(36.dp)
                            .width(75.dp)
                    ) {
                        Text(text = "Export", fontSize = 11.sp)
                    }

                    SmallFloatingActionButton(
                        onClick = { recentrerSurGPS() },
                        containerColor = Color(0xFF1976D2),
                        contentColor = Color.White,
                        modifier = Modifier
                            .height(36.dp)
                            .width(75.dp)
                    ) {
                        Text(text = "GPS", fontSize = 11.sp)
                    }

                    SmallFloatingActionButton(
                        onClick = { afficherDialogueReinitialisation = true },
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White,
                        modifier = Modifier
                            .height(36.dp)
                            .width(75.dp)
                    ) {
                        Text(text = "Réinit.", fontSize = 11.sp)
                    }
                }

                // DIALOGUE DE CONFIRMATION DE RÉINITIALISATION
                if (afficherDialogueReinitialisation) {
                    AlertDialog(
                        onDismissRequest = { afficherDialogueReinitialisation = false },
                        title = { Text("Réinitialisation") },
                        text = { Text("Êtes-vous sûr de vouloir remettre toutes les adresses à faire (rouge), sauf les inhabités ?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    reinitialiserStatutsAdresses()
                                    mapLibreMap?.style?.let { mettreAJourSource(it) }
                                    mettreAJourCompteurs()
                                    afficherDialogueReinitialisation = false
                                }
                            ) {
                                Text("Oui", color = Color(0xFFD32F2F))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { afficherDialogueReinitialisation = false }) {
                                Text("Annuler")
                            }
                        }
                    )
                }

                // DIALOGUE DE SÉLECTION D'ADRESSE
                dialogData?.let { (titreAdresse, listeLogements) ->
                    AlertDialog(
                        onDismissRequest = { dialogData = null },
                        title = { Text(titreAdresse) },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                listeLogements.forEach { (id, intitule) ->
                                    val couleurActuelle = coulesAdresses[id] ?: "red"
                                    var selection by remember { mutableStateOf(couleurActuelle) }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = intitule,
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )

                                            val options = listOf("À faire", "À repasser", "Fait", "Inhabité", "SPV")
                                            val codes = listOf("red", "orange", "green", "black", "blue")

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                codes.forEachIndexed { idx, code ->
                                                    val btnColor = when (code) {
                                                        "red" -> Color(0xFFD32F2F)
                                                        "orange" -> Color(0xFFFFA500)
                                                        "green" -> Color(0xFF2E7D32)
                                                        "blue" -> Color(0xFF1976D2)
                                                        else -> Color(0xFF212121)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            selection = code
                                                            coulesAdresses[id] = code
                                                            sauvegarderEtatFirebase(id, code)
                                                            mapLibreMap?.style?.let { mettreAJourSource(it) }
                                                            mettreAJourCompteurs()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (selection == code) btnColor else btnColor.copy(alpha = 0.3f)
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .padding(1.dp)
                                                    ) {
                                                        Text(
                                                            text = options[idx],
                                                            fontSize = 8.sp,
                                                            color = Color.White,
                                                            maxLines = 1
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { dialogData = null }) {
                                Text("Fermer")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun exporterResultatsCSV() {
        val features = featureCollection?.features()
        if (features.isNullOrEmpty()) {
            Toast.makeText(this, "Aucune adresse à exporter", Toast.LENGTH_SHORT).show()
            return
        }

        val csvBuilder = StringBuilder()
        csvBuilder.append("ID;Numero;Extension;Complement;Rue;Statut\n")

        features.forEach { feature ->
            val id = feature.getStringProperty("id") ?: ""
            val numero = feature.getStringProperty("numero") ?: ""
            val rep = if (feature.hasProperty("rep")) feature.getStringProperty("rep") else ""
            val complement = if (feature.hasProperty("complement")) feature.getStringProperty("complement") else ""
            val rue = feature.getStringProperty("rue") ?: ""

            val couleur = coulesAdresses[id] ?: "red"
            val statutText = when (couleur) {
                "orange" -> "A repasser"
                "green" -> "Fait"
                "black" -> "Inhabite"
                "blue" -> "SPV"
                else -> "A faire"
            }

            csvBuilder.append("$id;$numero;$rep;$complement;\"$rue\";$statutText\n")
        }

        try {
            val file = File(cacheDir, "export_tournee_${tourneeActuelle.lowercase()}.csv")
            file.writeText(csvBuilder.toString(), Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Export tournée $tourneeActuelle")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Partager l'export CSV"))
        } catch (e: Exception) {
            Log.e("ExportCSV", "Erreur lors de la création du fichier CSV", e)
            Toast.makeText(this, "Erreur lors de l'exportation", Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifierEtActiverLocalisation(style: Style) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            activerLocalisation(style)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun activerLocalisation(style: Style) {
        mapLibreMap?.let { map ->
            val locationComponent = map.locationComponent
            val options = LocationComponentActivationOptions.builder(this, style).build()

            locationComponent.activateLocationComponent(options)
            locationComponent.isLocationComponentEnabled = true
            locationComponent.cameraMode = CameraMode.NONE
            locationComponent.renderMode = RenderMode.COMPASS
        }
    }

    @SuppressLint("MissingPermission")
    private fun recentrerSurGPS() {
        mapLibreMap?.let { map ->
            val locationComponent = map.locationComponent
            if (locationComponent.isLocationComponentEnabled && locationComponent.lastKnownLocation != null) {
                val userLocation = locationComponent.lastKnownLocation
                if (userLocation != null) {
                    val latLng = LatLng(userLocation.latitude, userLocation.longitude)
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16.0))
                }
            } else {
                Toast.makeText(this, "Position GPS indisponible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun rechargerCarte(style: Style) {
        featureCollection = chargerAdresses()
        val totalFeatures = featureCollection?.features()?.size ?: 0
        Toast.makeText(this, "$totalFeatures adresses chargées", Toast.LENGTH_SHORT).show()

        val centre = if (tourneeActuelle.contains("CHATEAU_THEBAUD")) {
            LatLng(47.1235, -1.4170)
        } else {
            LatLng(47.0917, -1.3853)
        }
        mapLibreMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(centre, 13.0)
        )

        mettreAJourSource(style)
    }

    private fun normaliserTexte(texte: String): String {
        return texte.lowercase(Locale.FRENCH)
            .replace("’", "'")
            .replace("-", " ")
            .trim()
    }

    private fun chargerAdresses(): FeatureCollection {
        val features = mutableListOf<Feature>()

        val (reglesAppliquees, fichierJson) = when (tourneeActuelle) {
            "CHATEAU_THEBAUD_VIOLET" -> reglesChateauThebaudViolet to "adresses_chateau_thebaud.json"
            "CHATEAU_THEBAUD_JAUNE" -> reglesChateauThebaudJaune to "adresses_chateau_thebaud.json"
            else -> reglesMaisdon to "adresses_maisdon_sur_sevre.json"
        }

        val reglesNormalisees = reglesAppliquees.mapKeys { normaliserTexte(it.key) }

        try {
            val inputStream: InputStream = assets.open(fichierJson)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                val rue = obj.optString("nom_voie",
                    obj.optString("voie",
                        obj.optString("nom_rue",
                            obj.optString("rue",
                                obj.optString("nom_ach_fr",
                                    obj.optString("libelle", ""))))))

                val rueNormalisee = normaliserTexte(rue)
                val regleClé = reglesNormalisees.keys.firstOrNull { rueNormalisee.contains(it) || it.contains(rueNormalisee) }

                if (reglesNormalisees.isNotEmpty() && regleClé == null) {
                    continue
                }

                val numeroBrut = obj.optString("numero", obj.optString("num", ""))
                val numeroClean = numeroBrut.filter { it.isDigit() }
                val numeroInt = numeroClean.toIntOrNull()

                val filtre = if (regleClé != null) reglesNormalisees[regleClé] else "TOUS"
                if (numeroInt != null) {
                    val estPair = (numeroInt % 2 == 0)
                    if (filtre == "PAIR" && !estPair) continue
                    if (filtre == "IMPAIR" && estPair) continue
                }

                val id = obj.optString("id", obj.optString("id_ban_adresse", i.toString()))
                val rep = obj.optString("rep", obj.optString("extension", obj.optString("suffixe", "")))
                val complement = obj.optString("complement", "")

                var lat = 0.0
                var lon = 0.0

                if (obj.has("lat")) lat = obj.optDouble("lat", 0.0)
                else if (obj.has("latitude")) lat = obj.optDouble("latitude", 0.0)

                if (obj.has("lon")) lon = obj.optDouble("lon", 0.0)
                else if (obj.has("longitude")) lon = obj.optDouble("longitude", 0.0)

                if (lat == 0.0 && lon == 0.0 && obj.has("geometry")) {
                    val geom = obj.optJSONObject("geometry")
                    val coords = geom?.optJSONArray("coordinates")
                    if (coords != null && coords.length() >= 2) {
                        lon = coords.optDouble(0, 0.0)
                        lat = coords.optDouble(1, 0.0)
                    }
                }

                if (lat != 0.0 && lon != 0.0) {
                    val point = Point.fromLngLat(lon, lat)
                    val feature = Feature.fromGeometry(point)

                    feature.addStringProperty("id", id)
                    feature.addStringProperty("numero", numeroBrut)
                    feature.addStringProperty("rep", rep)
                    feature.addStringProperty("complement", complement)
                    feature.addStringProperty("rue", rue)
                    feature.addStringProperty("couleur", coulesAdresses[id] ?: "red")

                    features.add(feature)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erreur lors du chargement de $fichierJson", e)
            runOnUiThread {
                Toast.makeText(this, "Fichier introuvable : $fichierJson", Toast.LENGTH_LONG).show()
            }
        }
        return FeatureCollection.fromFeatures(features)
    }

    private fun mettreAJourSource(style: Style) {
        val featuresFiltrees = mutableListOf<Feature>()

        featureCollection?.features()?.forEach { feature ->
            val id = feature.getStringProperty("id")
            val couleur = coulesAdresses[id] ?: "red"
            feature.addStringProperty("couleur", couleur)

            val garder = when (filtreCouleur) {
                "RED" -> couleur == "red"
                "ORANGE" -> couleur == "orange"
                "GREEN" -> couleur == "green"
                "BLACK" -> couleur == "black"
                "BLUE" -> couleur == "blue"
                else -> true
            }

            if (garder) {
                featuresFiltrees.add(feature)
            }
        }

        val featuresVisuelsUniques = featuresFiltrees.groupBy {
            val num = it.getStringProperty("numero") ?: ""
            val rep = if (it.hasProperty("rep")) it.getStringProperty("rep") else ""
            val rue = it.getStringProperty("rue") ?: ""
            "$num-$rep-$rue"
        }.map { (_, listeLogements) ->
            val premier = listeLogements.first()

            val couleurs = listeLogements.map { it.getStringProperty("couleur") }
            val couleurGlobale = when {
                couleurs.contains("red") -> "red"
                couleurs.contains("orange") -> "orange"
                couleurs.contains("black") -> "black"
                couleurs.contains("blue") -> "blue"
                else -> "green"
            }

            val repAffichage = if (premier.hasProperty("rep")) premier.getStringProperty("rep") else ""
            val numAffichage = if (repAffichage.isNotBlank()) "${premier.getStringProperty("numero")} $repAffichage" else premier.getStringProperty("numero")

            val featureUnique = Feature.fromGeometry(premier.geometry())
            featureUnique.addStringProperty("numero", numAffichage)
            featureUnique.addStringProperty("rep", repAffichage)
            featureUnique.addStringProperty("rue", premier.getStringProperty("rue"))
            featureUnique.addStringProperty("couleur", couleurGlobale)

            featureUnique
        }

        val source = style.getSourceAs<GeoJsonSource>("adresses-source")
        source?.setGeoJson(FeatureCollection.fromFeatures(featuresVisuelsUniques))
    }

    private fun reinitialiserStatutsAdresses() {
        val misesAJour = mutableMapOf<String, Any>()

        featureCollection?.features()?.forEach { feature ->
            val id = feature.getStringProperty("id") ?: return@forEach
            val couleurActuelle = coulesAdresses[id] ?: "red"

            if (couleurActuelle != "black") {
                coulesAdresses[id] = "red"
                misesAJour[id] = "red"
            }
        }

        if (misesAJour.isNotEmpty()) {
            database.updateChildren(misesAJour)
        }
    }

    private fun sauvegarderEtatFirebase(id: String, couleur: String) {
        database.child(id).setValue(couleur)
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { super.onDestroy(); mapView.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}