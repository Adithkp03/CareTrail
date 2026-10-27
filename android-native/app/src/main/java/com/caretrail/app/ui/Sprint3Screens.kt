package com.caretrail.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caretrail.app.AppViewModel
import com.caretrail.app.data.Facility
import com.caretrail.app.data.FacilityApi
import com.caretrail.app.data.Geo
import com.caretrail.app.data.LastLocation
import com.caretrail.app.data.cachedFacilities
import com.caretrail.app.data.lastLocation
import com.caretrail.app.data.registration
import com.caretrail.app.data.saveCachedFacilities
import com.caretrail.app.data.saveLastLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun dial(ctx: Context, phone: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.trim()))) } catch (_: Exception) {}
}

private fun directions(ctx: Context, f: Facility) {
    try {
        val label = Uri.encode(f.name)
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${f.lat},${f.lon}?q=${f.lat},${f.lon}($label)")))
    } catch (_: Exception) {}
}

@Composable
private fun TabChip(label: String, selected: Boolean, onPick: () -> Unit) {
    Text(
        label, color = if (selected) Color.White else CT.Ink, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .background(if (selected) CT.Brand else CT.BrandSoft, RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onPick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun FacilityRow(f: Facility, l: L, ctx: Context) {
    CTCard(shape = RoundedCornerShape(16.dp), padding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CareIcon(if (f.kind == "blood_bank") "blood" else "hospital", 20.dp, tint = if (f.kind == "blood_bank") CT.Red700 else CT.Brand)
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.bodyMedium)
                if (f.address.isNotEmpty()) Muted(f.address)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Chip(l.tr(Geo.kindLabel(f.kind)), if (f.kind == "blood_bank") CT.Red50 else CT.BrandSoft, if (f.kind == "blood_bank") CT.Red800 else CT.Brand)
                    if (f.government) Chip(l.tr("Government"), CT.Green50, CT.Green800)
                    Chip("${f.distance_km} km", CT.Ink.copy(alpha = 0.06f), CT.Ink)
                }
            }
        }
        Spacer(gap(8))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(l.tr("Directions"), color = CT.Brand, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { directions(ctx, f) }.padding(vertical = 4.dp))
            if (f.phone.isNotEmpty()) Text(l.tr("Call"), color = CT.Brand, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { dial(ctx, f.phone) }.padding(vertical = 4.dp))
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun FacilitiesScreen(vm: AppViewModel, nav: Nav) {
    val l = rememberL(vm)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { FacilityApi() }
    JourneyGate(vm, onEmpty = nav.newJourney) { j, _, _ ->
        val reg = remember(j.journey_id) { vm.store.registration(j.journey_id) }
        var tab by remember { mutableStateOf("facilities") }
        var location by remember { mutableStateOf(vm.store.lastLocation()) }
        var facilities by remember { mutableStateOf(vm.store.cachedFacilities()) }
        var fromCache by remember { mutableStateOf(facilities.isNotEmpty()) }
        var pin by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }

        fun load(loc: LastLocation) {
            location = loc
            vm.store.saveLastLocation(loc)
            busy = true; error = ""
            scope.launch {
                try {
                    val found = withContext(Dispatchers.IO) { api.nearby(loc.lat, loc.lon, 10) }
                    facilities = found
                    vm.store.saveCachedFacilities(found)
                    fromCache = false
                    if (found.isEmpty()) error = ""
                } catch (e: Exception) {
                    error = l.tr("Could not load facilities right now. Check your connection and try again; any cached list below may be old.")
                }
                busy = false
            }
        }

        @SuppressLint("MissingPermission")
        fun useGps() {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val last = try { lm.getProviders(true).firstNotNullOfOrNull { p -> lm.getLastKnownLocation(p) } } catch (_: Exception) { null }
            if (last != null && (System.currentTimeMillis() - last.time) < 10 * 60 * 1000) {
                load(LastLocation(last.latitude, last.longitude, "GPS"))
                return
            }
            busy = true
            try {
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(loc: android.location.Location) {
                        try { lm.removeUpdates(this) } catch (_: Exception) {}
                        load(LastLocation(loc.latitude, loc.longitude, "GPS"))
                    }
                    @Deprecated("deprecated in Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {
                        try { lm.removeUpdates(this) } catch (_: Exception) {}
                        busy = false; error = l.tr("Could not get your GPS position. Enter your PIN code instead.")
                    }
                }
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null)
            } catch (e: Exception) { busy = false; error = l.tr("Could not get your GPS position. Enter your PIN code instead.") }
        }

        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) useGps() else error = l.tr("Location permission was not given. Enter your PIN code instead.")
        }

        Page(title = l.tr("Facilities near you"), back = nav.home) {
            Spacer(gap(8))
            // Assigned ASHA / VHN - from the first-visit checklist, always offline.
            CTCard {
                SectionTitle(l.tr("Your health workers"))
                if (reg.asha_name.isBlank() && reg.vhn_name.isBlank()) {
                    Muted(l.tr("Add your ASHA and Village Health Nurse in the first-visit checklist so they are one tap away here."))
                    Spacer(gap(8))
                    OutlineButton(l.tr("Open first-visit checklist"), onClick = nav.registration)
                } else {
                    if (reg.asha_name.isNotBlank()) {
                        Spacer(gap(6))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CareIcon("visit", 18.dp)
                            Text("${l.tr("ASHA")}: ${reg.asha_name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (reg.asha_phone.isNotBlank()) Text(l.tr("Call"), color = CT.Brand, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable { dial(ctx, reg.asha_phone) }.padding(4.dp))
                        }
                    }
                    if (reg.vhn_name.isNotBlank()) {
                        Spacer(gap(6))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CareIcon("visit", 18.dp)
                            Text("${l.tr("VHN")}: ${reg.vhn_name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (reg.vhn_phone.isNotBlank()) Text(l.tr("Call"), color = CT.Brand, style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clickable { dial(ctx, reg.vhn_phone) }.padding(4.dp))
                        }
                    }
                }
            }
            Spacer(gap(12))
            CTCard {
                SectionTitle(l.tr("Where should we look?"))
                Muted(l.tr("Facilities within 10 km of this point: hospitals, clinics and PHCs, doctors and blood banks (open map data)."))
                location?.let { Spacer(gap(6)); Muted("📍 " + it.label) }
                Spacer(gap(8))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { CTField(pin, { pin = it.filter { c -> c.isDigit() }.take(6) }, l.tr("PIN code"), keyboard = KeyboardType.Number) }
                    Column { PrimaryButton(l.tr("Find"), enabled = !busy && pin.length == 6) {
                        busy = true; error = ""
                        scope.launch {
                            try { load(withContext(Dispatchers.IO) { api.geocodePin(pin) }) }
                            catch (e: Exception) { error = l.tr("Place not found - check the PIN code."); busy = false }
                        }
                    } }
                }
                Spacer(gap(8))
                OutlineButton("📍 " + l.tr("Use my GPS location"), enabled = !busy) {
                    permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (busy) { Spacer(gap(8)); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(Modifier.size(18.dp), color = CT.Brand); Muted(l.tr("Searching within 10 km...")) } }
                if (error.isNotEmpty()) { Spacer(gap(8)); Text(error, color = CT.Amber900, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(gap(12))
            if (fromCache && facilities.isNotEmpty()) { Note(l.tr("Showing the last loaded list (works offline). Search again to refresh."), tone = Tone.Blue); Spacer(gap(8)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabChip(l.tr("Hospitals & clinics"), tab == "facilities") { tab = "facilities" }
                TabChip(l.tr("Blood banks"), tab == "blood") { tab = "blood" }
            }
            Spacer(gap(8))
            val shown = facilities.filter { if (tab == "blood") it.kind == "blood_bank" else it.kind != "blood_bank" }
            if (shown.isEmpty() && !busy) {
                CTCard {
                    Muted(
                        if (facilities.isEmpty()) l.tr("Search above to see facilities within 10 km.")
                        else if (tab == "blood") l.tr("No blood bank in open map data near this point. Call 108 or your ASHA worker for the nearest blood bank.")
                        else l.tr("No hospitals or clinics found in open map data near this point.")
                    )
                }
            }
            shown.forEach { f -> Spacer(gap(8)); FacilityRow(f, l, ctx) }
            Spacer(gap(12))
            Muted(l.tr("Distances are straight-line, not road distance. Directions open in your maps app for the real route. Blood bank stock changes daily - always call ahead."))
            Spacer(gap(4))
            Muted(l.tr("Facility data: OpenStreetMap contributors."))
        }
    }
}
