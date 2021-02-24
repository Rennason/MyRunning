package com.sdeoliveira.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.sdeoliveira.maps.services.PoliLine
import com.sdeoliveira.maps.services.TrackingService
import java.util.*
import kotlin.math.round
import androidx.lifecycle.Observer as Observer


class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMyLocationButtonClickListener, GoogleMap.OnMyLocationClickListener {

    private lateinit var map: GoogleMap //lateinit: rastrear a var "map" do tipo "GoogleMap" e depois reinicia-la
    private val toggle_button by lazy { findViewById<ToggleButton>(R.id.toggleButton) }
    private val textView1 by lazy { findViewById<TextView>(R.id.textView1)}
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var latIni: Double = 0.0
    private var latFinal: Double = 0.0
    private var longIni: Double = 0.0
    private var longFinal: Double = 0.0
    private var weight = 150f
    
    private var isTracking = false
    private var pathPoints = mutableListOf<PoliLine>()

    private var curTimeMillis = 0L

    companion object {
        const val REQUEST_CODE_LOCATION = 0
    }

    private fun subscribeToObservers(){
        TrackingService.isTracking.observe(this,  Observer {
            updateTracking(it)
        })

        TrackingService.pathPoints.observe(this, Observer {
            pathPoints = it
            addLatestPoliline()
        })

        TrackingService.timeRunMiliSeconds.observe(this, Observer{
            curTimeMillis = it
            //val formattedTime = TrackingUtility.getFormattedStopWatchTime(curTimeMillis, true)
            //tvTimer.text = formattedTime
            //textView1.text = curTimeMillis.toString()
            Log.d("Tempo", curTimeMillis.toString())
        })
    }

    private fun toggleRun(){
        if(isTracking)
        {
            sendCommandToService("ACTION_PAUSE_SERVICE")
        }
        else{
            sendCommandToService("ACTION_START_OR_RESUME_SERVICE")
        }
    }

    private fun updateTracking(isTracking : Boolean){
        this.isTracking = isTracking

        /*if(!isTracking){
            toggle
        }*/
    }

    private fun addAllPoliline(){
        for(poliline in pathPoints){
            val polilineOptions= PolylineOptions()
                .color(Color.RED)
                .width(8f)
                .addAll(poliline)
            map?.addPolyline(polilineOptions)
        }
    }

    private fun endRun(): Int {
        var distanceMeters = 0
        for (poliline in pathPoints){
            distanceMeters += calculatePolilineLength(poliline).toInt()
        }

        var distanceKm = distanceMeters/1000f

        val avgSpeed = round( distanceKm / (curTimeMillis/1000f/60/60) * 10)/10f //converte milisegundos para hora e distancia em km
        val calories = (distanceKm * weight).toInt()

        return calories
    }


    private fun calculatePolilineLength(poliline : PoliLine) : Float {
        var distance = 0f
        for(i in 0..poliline.size - 2)
        {
            val posA = poliline[i]
            val posB = poliline[i+1]

            val result = FloatArray(1)

            Location.distanceBetween(posA.latitude, posA.longitude, posB.latitude, posB.longitude, result)

            distance += result[0]
        }
        return distance
    }

    private fun addLatestPoliline(){
        if(pathPoints.isNotEmpty() && pathPoints.last().size > 1){
            val preLastLong = pathPoints.last()[pathPoints.last().size - 2]
            val lastLastLong = pathPoints.last().last()
            val polilineOptions = PolylineOptions()
                .color(Color.RED)
                .width(8f)
                .add(preLastLong)
                .add(lastLastLong)
            map?.addPolyline(polilineOptions)
        }
    }

    // A funçao será chamada quando o mapa carregar
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap // Carregar mapa
        createMarker() // Criar marcação
        map.setOnMyLocationButtonClickListener(this) // Criar botão Localizar
        map.setOnMyLocationClickListener(this) // Mostrar latitude e longitude no mapa
        enableLocation() // Ativar localização
        setMapLongClick(this.map) // Adicionar marcador com um click longo
        setPoiClick(map) // Adicionar ponto de interesse (POIs)
    }

    private fun createMarker() {
        val coordenadas =
            LatLng(1.353291, 103.806107) // Posição em Coordenadas latitude e longitude qualquer
        val marker: MarkerOptions = MarkerOptions().position(coordenadas)
            .title("Reservoir Park - Singapura") // Marca: val coordenadas e nomeia o lugar com: .title
        map.addMarker(marker) // add no map a val marker
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(coordenadas, 10f),
            4000,
            null
        ) // Configurar ZOOM de val coordenadas, 10f: quanto de Zoom, 6000: velocidade do Zoom 4.5s
        Toast.makeText(this, "Click no marcador para mais informações", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        createFragment() // método criar mapa

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext!!)

        toggle_button.setOnCheckedChangeListener { buttonView, isChecked ->
            //sendCommandToService("ACTION_START_OR_RESUME_SERVICE")
            toggleRun()

            if(isChecked)
            {
                textView1.text = "${endRun()} cal"
            }
            else
            {
                textView1.text = ""
            }
        }
        subscribeToObservers()
    }

    // Fragment para carregar o mapa
    private fun createFragment() {
        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment // função para criar mapa do tipo: as SupportMapFragment
        mapFragment.getMapAsync(this) // chamada para OnMapReadyCallback
        addAllPoliline()
    }

    // Função para verificar/comparar se a permissão de localização  esta ativada
    private fun isLocationPermissionGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun enableLocation() {
        if (!::map.isInitialized) return // caso o mapa não tenha sido inicializado retorne...
        if (isLocationPermissionGranted()) {
            map.isMyLocationEnabled = true // Verifica permissão ativada
        } else {
            requestLocationPermission() // Verifica permissão não ativada
        }
    }

    // Função solicitar permissão de localização
    private fun requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) { // Pedido para ativar permissão
            Toast.makeText(
                this,
                "Ative permissões para usar o botão Localização em tempo real",
                Toast.LENGTH_LONG
            ).show() // Toast ativar permissão
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE_LOCATION
            )
        }
    }

    // Verifica a resposta da solicitação
    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) { // Quando: REQUEST_CODE_LOCATION não esta vazia && const val for 0
            REQUEST_CODE_LOCATION -> if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                map.isMyLocationEnabled = true // Ativa localização em tempo real
            } else {
                Toast.makeText(
                    this,
                    "Para carregar sua localização vá em ajustes e ative permissões",
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {
            }
        }
    }

    private fun sendCommandToService(action: String) =
        Intent(this, TrackingService::class.java).also {
            it.action = action
            startService(it)
        }

    @SuppressLint("MissingPermission")
    override fun onResumeFragments() {
        super.onResumeFragments()
        if (!::map.isInitialized) return
        if (!isLocationPermissionGranted()) {
            map.isMyLocationEnabled = false
            Toast.makeText(
                this,
                "Vá em ajustes e ative permissões de localização",
                Toast.LENGTH_SHORT
            ).show()
        }

    }

    // Botão Localizar do canto superior direito da tela
    override fun onMyLocationButtonClick(): Boolean {
        Toast.makeText(this, "Click no ponto azul para ver coordenadas", Toast.LENGTH_SHORT).show()
        return false
    }

    // Mostrar Latitude e Longitude no mapa
    override fun onMyLocationClick(p0: Location) {
        Toast.makeText(
            this,
            "Latitude: ${p0.latitude}, Longitude: ${p0.longitude}",
            Toast.LENGTH_LONG
        ).show()
    }

    // Criar menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.map_options, menu)
        return true
    }

    // Itens de opções do menu
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // Adiciona um marcador usando um click longo no mapa
    private fun setMapLongClick(map: GoogleMap) {

        map.setOnMapLongClickListener { latLng ->

            val snippets = String.format(
                Locale.getDefault(),
                "Latitude: %1$.5f, Longitude: %2$.5f",
                latLng.latitude,
                latLng.longitude
            )

            map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.dropped_pin))
                    .snippet(snippets)
            )
        }
    }

    // Adicionar ponto de interesse (POIs). Coloca um marcador no mapa quando o usuário
    // clica em um POI e exibe uma janela de informações.
    private fun setPoiClick(map: GoogleMap) {
        map.setOnPoiClickListener { poi ->

            val poiMarker = map.addMarker(
                MarkerOptions()
                    .position(poi.latLng)
                    .title(poi.name)
            )
            poiMarker.showInfoWindow()
        }
    }
}
