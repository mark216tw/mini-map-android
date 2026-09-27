package com.example.minimap

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val database by lazy {
        Room.databaseBuilder(applicationContext, PlacesDatabase::class.java, "places.db").build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (Build.VERSION.SDK_INT >= 27) {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.dark(android.graphics.Color.rgb(22, 68, 92))
            }
        )
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        Configuration.getInstance().apply {
            userAgentValue = "miniMap-Android/1.0 (${applicationContext.packageName})"
            osmdroidBasePath = File(filesDir, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles").apply { mkdirs() }
        }
        setContent {
            MaterialTheme { MiniMapScreen(database.places()) }
        }
    }
}

@Composable
private fun MiniMapScreen(dao: PlaceDao) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("map_settings", android.content.Context.MODE_PRIVATE) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val places by remember(dao) { dao.observeAll() }.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchPin by remember { mutableStateOf<SearchResult?>(null) }
    var location by remember { mutableStateOf<GeoPoint?>(null) }
    var editing by remember { mutableStateOf<Place?>(null) }
    var previewing by remember { mutableStateOf<Place?>(null) }
    var showPlaces by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Place?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(!preferences.getBoolean("long_press_hint_seen", false)) }
    BackHandler(enabled = showPlaces && editing == null && previewing == null && deleting == null) { showPlaces = false }
    val map = remember {
        MapView(context).apply {
            setBackgroundColor(android.graphics.Color.rgb(232, 245, 239))
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            val lat = Double.fromBits(preferences.getLong("map_lat", 20.0.toBits()))
            val lon = Double.fromBits(preferences.getLong("map_lon", 0.0.toBits()))
            val zoom = Double.fromBits(preferences.getLong("map_zoom", 3.0.toBits()))
            controller.setZoom(if (zoom.isFinite() && zoom in 0.0..19.0) zoom else 3.0)
            controller.setCenter(GeoPoint(
                if (lat.isFinite() && lat in -85.0..85.0) lat else 20.0,
                if (lon.isFinite() && lon in -180.0..180.0) lon else 0.0
            ))
        }
    }

    fun message(text: String) { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    fun dismissHint() {
        showHint = false
        preferences.edit().putBoolean("long_press_hint_seen", true).apply()
    }
    fun saveViewport() {
        val center = map.mapCenter
        preferences.edit()
            .putLong("map_lat", center.latitude.toBits())
            .putLong("map_lon", center.longitude.toBits())
            .putLong("map_zoom", map.zoomLevelDouble.toBits())
            .apply()
    }
    fun goTo(lat: Double, lon: Double) {
        map.controller.setZoom(16.0)
        map.controller.animateTo(GeoPoint(lat, lon))
    }
    fun addSearchResult(result: SearchResult) {
        searchPin = result
        results = emptyList()
        goTo(result.latitude, result.longitude)
        editing = Place(
            UUID.randomUUID().toString(),
            result.name.substringBefore(',').trim().take(100).ifBlank { "未命名地標" },
            "",
            result.latitude,
            result.longitude
        )
    }
    fun submitSearch() {
        val searchText = query.trim()
        if (busy || searchText.isEmpty()) return
        keyboard?.hide()
        scope.launch {
            busy = true
            results = emptyList()
            try {
                results = OnlineServices.search(searchText)
                if (results.isEmpty()) message("找不到地點")
            } catch (e: Exception) { message("搜尋失敗，請檢查網路：${e.localizedMessage}") }
            finally { busy = false }
        }
    }
    fun locate() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return
        scope.launch {
            busy = true
            try {
                val fix = OnlineServices.currentLocation(context)
                if (fix == null) message("無法取得位置，請確認已開啟定位")
                else {
                    location = GeoPoint(fix.latitude, fix.longitude)
                    goTo(fix.latitude, fix.longitude)
                }
            } catch (_: SecurityException) {
                message("請允許定位權限")
            } finally { busy = false }
        }
    }
    fun shareCurrentLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return
        scope.launch {
            busy = true
            try {
                val fix = OnlineServices.currentLocation(context)
                if (fix == null) {
                    message("無法取得位置，請確認已開啟定位")
                } else {
                    val text = LocationShare.format(fix.latitude, fix.longitude, LocalDateTime.now())
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享目前位置"))
                }
            } catch (_: SecurityException) {
                message("請允許定位權限")
            } catch (_: ActivityNotFoundException) {
                message("找不到可分享文字的 APP")
            } catch (e: Exception) {
                message("分享位置失敗：${e.localizedMessage}")
            } finally { busy = false }
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) locate() else message("未取得定位權限；仍可使用地圖與地標")
    }
    val sharePermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) shareCurrentLocation() else message("未取得定位權限，無法分享目前位置")
    }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/geo+json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val content = withContext(Dispatchers.IO) { GeoJson.encode(dao.getAll()) }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                        ?: error("無法開啟匯出檔案")
                }
                message("GeoJSON 匯出完成")
            } catch (e: Exception) { message("匯出失敗：${e.localizedMessage}") }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 5_000_000) { "匯入檔案超過 5 MB" }
                            output.write(buffer, 0, count)
                        }
                        output.toString(Charsets.UTF_8.name())
                    } ?: error("無法讀取檔案")
                    val (items, invalid) = GeoJson.decode(text)
                    var added = 0
                    for (place in items) if (dao.insert(place) != -1L) added++
                    Triple(added, items.size - added, invalid)
                }
                message("匯入完成：新增 ${report.first} 筆、重複跳過 ${report.second} 筆、無效 ${report.third} 筆")
            } catch (e: Exception) { message("匯入失敗：${e.localizedMessage}") }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFE8F5EF))) {
        MapCanvas(
            map = map, places = places, searchPin = searchPin, location = location,
            onLongPress = { point ->
                if (showHint) dismissHint()
                editing = Place(UUID.randomUUID().toString(), "", "", point.latitude, point.longitude)
            },
            onMarker = { previewing = it },
            onSearchMarker = { searchPin?.let(::addSearchResult) },
            onPause = ::saveViewport
        )

        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Column(Modifier.align(Alignment.TopCenter).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.weight(1f), tonalElevation = 5.dp,
                    shadowElevation = 4.dp, shape = CircleShape) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("搜尋地點") }, singleLine = true,
                        shape = CircleShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                        trailingIcon = {
                            IconButton(onClick = { submitSearch() }, enabled = !busy && query.isNotBlank()) {
                                Icon(painterResource(R.drawable.ic_search), contentDescription = "查詢")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { showPlaces = true }, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_place), contentDescription = "我的地標")
                }
            }
            if (showHint) {
                Spacer(Modifier.height(6.dp))
                Surface(tonalElevation = 5.dp, shadowElevation = 3.dp,
                    shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("長按地圖新增地標", modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = ::dismissHint) { Text("知道了") }
                    }
                }
            }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Surface(tonalElevation = 5.dp, shadowElevation = 4.dp, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        results.forEach { result ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    searchPin = result
                                    goTo(result.latitude, result.longitude)
                                    results = emptyList()
                                }, modifier = Modifier.weight(1f)) {
                                    Text(result.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth())
                                }
                                TextButton(onClick = { addSearchResult(result) }) { Text("加入地標") }
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            searchPin?.let { result ->
                Button(onClick = { addSearchResult(result) }) { Text("＋ 加入我的地標") }
            }
            FilledIconButton(enabled = !busy, modifier = Modifier.size(48.dp), onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ) shareCurrentLocation()
                else sharePermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = "分享目前位置")
            }
            FilledIconButton(enabled = !busy, modifier = Modifier.size(48.dp), onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ) locate()
                else permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) {
                Icon(painterResource(R.drawable.ic_my_location), contentDescription = "定位")
            }
            Surface(shape = MaterialTheme.shapes.small, tonalElevation = 4.dp) {
                Text("地圖 © OpenStreetMap 貢獻者\n查詢 © OpenStreetMap / Nominatim",
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp))
            }
        }
        }
        if (showPlaces) {
            PlacesScreen(
                places = places,
                modifier = Modifier.fillMaxSize(),
                onGoTo = { place ->
                    keyboard?.hide()
                    showPlaces = false
                    goTo(place.latitude, place.longitude)
                },
                onEdit = { keyboard?.hide(); editing = it },
                onExport = { createFile.launch("mini-map-places.geojson") },
                onImport = { openFile.launch(arrayOf("application/geo+json", "application/json", "*/*")) },
                onClose = { showPlaces = false }
            )
        }
    }
    previewing?.let { place ->
        AlertDialog(
            onDismissRequest = { previewing = null },
            title = { Text(place.name) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (place.note.isNotBlank()) {
                        Text(place.note)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("緯度：${place.latitude}\n經度：${place.longitude}",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { previewing = null; editing = place }) { Text("編輯") } },
            dismissButton = { TextButton(onClick = { previewing = null }) { Text("關閉") } }
        )
    }
    editing?.let { place ->
        val isNew = places.none { it.id == place.id }
        PlaceDialog(place = place, isNew = isNew,
            onDismiss = { editing = null },
            onSave = { name, note ->
                scope.launch {
                    val changed = withContext(Dispatchers.IO) {
                        if (isNew) dao.insert(place.copy(name = name, note = note)) != -1L
                        else dao.updateDetails(place.id, name, note) > 0
                    }
                    message(if (changed) "地標已儲存" else "此座標已有地標")
                    editing = null
                }
            },
            onDelete = { deleting = place; editing = null }
        )
    }
    deleting?.let { place ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("刪除地標？") },
            text = { Text("確定刪除「${place.name}」？") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { dao.delete(place.id) }
                    deleting = null
                    message("地標已刪除")
                }
            }) { Text("刪除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
}

@Composable
private fun PlacesScreen(
    places: List<Place>, modifier: Modifier = Modifier,
    onGoTo: (Place) -> Unit, onEdit: (Place) -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, onClose: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var filter by remember { mutableStateOf("") }
    val matchingPlaces = remember(places, filter) {
        val term = filter.trim()
        if (term.isEmpty()) places else places.filter {
            it.name.contains(term, ignoreCase = true) || it.note.contains(term, ignoreCase = true)
        }
    }
    // Keep touches in the full-screen page from reaching the map behind it.
    Surface(modifier = modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Final).changes.forEach { it.consume() }
            }
        }
    }, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回地圖")
                }
                Text("我的地標", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 12.dp))
            }
            HorizontalDivider()
            OutlinedTextField(
                value = filter, onValueChange = { filter = it },
                placeholder = { Text("搜尋地標名稱或備註") }, singleLine = true,
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (matchingPlaces.isEmpty()) {
                    item {
                        Text(if (filter.isBlank()) "還沒有地標，長按地圖就能新增。" else "找不到符合的地標",
                            modifier = Modifier.padding(24.dp))
                    }
                }
                items(matchingPlaces, key = { it.id }) { place ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier.weight(1f)
                                .clickable { keyboard?.hide(); onGoTo(place) }
                                .padding(vertical = 16.dp, horizontal = 8.dp)
                        ) {
                            Text(place.name, style = MaterialTheme.typography.titleMedium)
                            if (place.note.isNotBlank()) {
                                Text(place.note, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(onClick = { keyboard?.hide(); onEdit(place) }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = "編輯${place.name}")
                        }
                    }
                    HorizontalDivider()
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onExport) { Text("匯出") }
                TextButton(onClick = onImport) { Text("匯入") }
                TextButton(onClick = onClose) { Text("關閉") }
            }
        }
    }
}

@Composable
private fun MapCanvas(
    map: MapView, places: List<Place>, searchPin: SearchResult?, location: GeoPoint?,
    onLongPress: (GeoPoint) -> Unit, onMarker: (Place) -> Unit,
    onSearchMarker: () -> Unit, onPause: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestLongPress by rememberUpdatedState(onLongPress)
    val latestMarker by rememberUpdatedState(onMarker)
    val latestSearchMarker by rememberUpdatedState(onSearchMarker)
    val latestOnPause by rememberUpdatedState(onPause)
    val events = remember(map) {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = false
            override fun longPressHelper(p: GeoPoint): Boolean {
                latestLongPress(p)
                return true
            }
        })
    }
    DisposableEffect(map, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> { latestOnPause(); map.onPause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            latestOnPause()
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize()) { view ->
        view.overlays.clear()
        view.overlays.add(events)
        places.forEach { place ->
            view.overlays.add(Marker(view).apply {
                position = GeoPoint(place.latitude, place.longitude)
                title = place.name
                icon = ContextCompat.getDrawable(view.context, R.drawable.ic_map_place)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ -> latestMarker(place); true }
            })
        }
        searchPin?.let { pin ->
            view.overlays.add(Marker(view).apply {
                position = GeoPoint(pin.latitude, pin.longitude)
                title = pin.name
                snippet = "搜尋結果"
                icon = ContextCompat.getDrawable(view.context, R.drawable.ic_map_search)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ -> latestSearchMarker(); true }
            })
        }
        location?.let { point ->
            view.overlays.add(Marker(view).apply {
                position = point
                title = "目前位置"
                icon = ContextCompat.getDrawable(view.context, R.drawable.ic_map_location)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            })
        }
        view.invalidate()
    }
}

@Composable
private fun PlaceDialog(
    place: Place, isNew: Boolean, onDismiss: () -> Unit,
    onSave: (String, String) -> Unit, onDelete: () -> Unit
) {
    var name by remember(place.id) { mutableStateOf(place.name) }
    var note by remember(place.id) { mutableStateOf(place.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新增地標" else "編輯地標") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { if (it.length <= 100) name = it }, label = { Text("名稱") }, singleLine = true)
                OutlinedTextField(note, { if (it.length <= 2000) note = it }, label = { Text("備註") }, maxLines = 4)
                Text("緯度：${place.latitude}\n經度：${place.longitude}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), note) }) { Text("儲存") } },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("刪除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
