package com.example.mymaps

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.core.content.ContextCompat

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.mymaps.databinding.ActivityCreateMapBinding
import com.example.mymaps.models.Place
import com.example.mymaps.models.UserMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.material.snackbar.Snackbar

private const val TAG = "CreateMapActivity"

class CreateMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityCreateMapBinding
    private var markers: MutableList<Marker> = mutableListOf()
    
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
        binding = ActivityCreateMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        mapFragment.view?.let {
            Snackbar.make(it, "Long press to add a marker!", Snackbar.LENGTH_LONG)
                .setAction("Ok") {}
                .setActionTextColor(ContextCompat.getColor(this, android.R.color.white))
                .show()
        }

        supportActionBar?.title = intent.getStringExtra(EXTRA_MAP_TITLE) ?: "Create map"
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_create_map, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.miSave) {
            Log.i(TAG, "Tapped on save!")
            if (markers.isEmpty()) {
                Toast.makeText(this, "There must be at least one marker on the map", Toast.LENGTH_LONG).show()
                return true
            }

            val places = markers.mapNotNull { marker ->
                val title = marker.title
                val description = marker.snippet
                val data = marker.tag as? MarkerExtraData
                if (title != null && description != null && data != null) {
                    Place(title, description, marker.position.latitude, marker.position.longitude, data.hue, data.timestamp)
                } else null
            }

            val userMap = intent.getStringExtra(EXTRA_MAP_TITLE)?.let {
                UserMap(it, places)
            }

            val data = Intent()
            data.putExtra(EXTRA_USER_MAP, userMap)
            setResult(Activity.RESULT_OK, data)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.setOnInfoWindowClickListener { markerToDelete ->
            Log.i(TAG, "Deleting marker: ${markerToDelete.title}")
            markers.remove(markerToDelete)
            markerToDelete.remove()
        }

        mMap.setOnMapLongClickListener { latLng ->
            Log.i(TAG, "Long press at $latLng")
            showAlertDialogue(latLng)
        }
        val vancouver = LatLng(49.2827, -123.1207)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(vancouver, 12f))
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
