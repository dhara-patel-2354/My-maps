package com.example.mymaps

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.mymaps.databinding.ActivityDisplayMapBinding
import com.example.mymaps.models.Place
import com.example.mymaps.models.UserMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "DisplayMapActivity"

class DisplayMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var userMap: UserMap
    private lateinit var binding: ActivityDisplayMapBinding
    private val markers = mutableListOf<Marker>()
    
    private val colorNames = listOf("Red", "Blue", "Green", "Yellow", "Orange", "Violet", "Azure", "Rose")
    private val colorHues = listOf(
        BitmapDescriptorFactory.HUE_RED,
        BitmapDescriptorFactory.HUE_BLUE,
        BitmapDescriptorFactory.HUE_GREEN,
        BitmapDescriptorFactory.HUE_YELLOW,
        BitmapDescriptorFactory.HUE_ORANGE,
        BitmapDescriptorFactory.HUE_VIOLET,
        BitmapDescriptorFactory.HUE_AZURE,
        BitmapDescriptorFactory.HUE_ROSE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDisplayMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val retrieved: UserMap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_USER_MAP, UserMap::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_USER_MAP) as? UserMap
        }

        if (retrieved == null) {
            Log.e(TAG, "UserMap was null, finishing activity")
            finish()
            return
        }
        userMap = retrieved

        supportActionBar?.title = userMap.title

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_display_map, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.miUpdate) {
            updateMapInFirestore()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateMapInFirestore() {
        val docId = userMap.id
        if (docId == null) {
            Toast.makeText(this, "Sample maps cannot be modified and saved.", Toast.LENGTH_LONG).show()
            return
        }

        val updatedPlaces = markers.mapNotNull { marker ->
            val title = marker.title
            val description = marker.snippet
            val data = marker.tag as? MarkerExtraData
            if (title != null && description != null && data != null) {
                Place(title, description, marker.position.latitude, marker.position.longitude, data.hue, data.timestamp)
            } else null
        }

        val updatedMap = userMap.copy(places = updatedPlaces)
        val db = Firebase.firestore
        db.collection("maps").document(docId).set(updatedMap)
            .addOnSuccessListener {
                Toast.makeText(this, "Map updated successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating map", e)
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? {
                return null // Use default frame
            }

            override fun getInfoContents(marker: Marker): View {
                val view = layoutInflater.inflate(R.layout.custom_info_window, null)
                val tvName = view.findViewById<TextView>(R.id.tvInfoWindowName)
                val tvDesc = view.findViewById<TextView>(R.id.tvInfoWindowDescription)
                val tvDate = view.findViewById<TextView>(R.id.tvInfoWindowDate)

                val data = marker.tag as? MarkerExtraData
                tvName.text = marker.title
                tvDesc.text = marker.snippet
                
                if (data != null) {
                    val sdf = SimpleDateFormat("yyyy MMM dd", Locale.getDefault())
                    tvDate.text = "Created: ${sdf.format(Date(data.timestamp))}"
                }

                return view
            }
        })

        mMap.setOnInfoWindowClickListener { markerToDelete ->
            markers.remove(markerToDelete)
            markerToDelete.remove()
        }

        mMap.setOnMapLongClickListener { latLng ->
            showAlertDialogue(latLng)
        }

        val boundsBuilder = LatLngBounds.Builder()
        for (place in userMap.places) {
            val latLng = LatLng(place.latitude, place.longitude)
            boundsBuilder.include(latLng)
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(place.title)
                    .snippet(place.description)
                    .icon(BitmapDescriptorFactory.defaultMarker(place.colorHue))
            )
            marker?.tag = MarkerExtraData(place.colorHue, place.creationTimestamp)
            marker?.let { markers.add(it) }
        }

        if (userMap.places.isNotEmpty()) {
            val bounds = boundsBuilder.build()
            val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
            mapFragment.view?.post {
                if (userMap.places.size == 1) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(bounds.center, 12f))
                } else {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
                }
            }
        }
    }

    private fun showAlertDialogue(latLng: LatLng) {
        val placeFormView = LayoutInflater.from(this).inflate(R.layout.dialog_create_space, null)
        val spinnerColor = placeFormView.findViewById<Spinner>(R.id.spinnerColor)
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colorNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerColor.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setTitle("Create a marker")
            .setView(placeFormView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Ok", null)
            .show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val title = placeFormView.findViewById<EditText>(R.id.etTitle).text.toString()
            val description = placeFormView.findViewById<EditText>(R.id.etDescription).text.toString()
            val selectedColorIndex = spinnerColor.selectedItemPosition
            val hue = colorHues[selectedColorIndex]

            if (title.trim().isEmpty() || description.trim().isEmpty()) {
                Toast.makeText(this, "Place must have a non-empty title and description", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val timestamp = System.currentTimeMillis()
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(title)
                    .snippet(description)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
            )
            marker?.tag = MarkerExtraData(hue, timestamp)
            marker?.let { markers.add(it) }
            dialog.dismiss()
        }
    }

    data class MarkerExtraData(val hue: Float, val timestamp: Long)
}
