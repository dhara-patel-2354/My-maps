package com.example.mymaps

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mymaps.models.Place
import com.example.mymaps.models.UserMap
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

const val EXTRA_USER_MAP = "EXTRA_USER_MAP"
const val EXTRA_MAP_TITLE = "EXTRA_MAP_TITLE"
private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {
    private lateinit var rvMaps: RecyclerView
    private lateinit var fabCreateMap: FloatingActionButton
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var llEmptyState: android.widget.LinearLayout
    private lateinit var mapAdapter: MapsAdapter
    private var userMaps = mutableListOf<UserMap>()
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val getMapResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val userMap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                result.data?.getSerializableExtra(EXTRA_USER_MAP, UserMap::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getSerializableExtra(EXTRA_USER_MAP) as? UserMap
            }

            if (userMap != null) {
                // Attach userId
                val currentUid = auth.currentUser?.uid ?: ""
                val mapWithUser = userMap.copy(userId = currentUid)
                
                // Save to Firestore
                db.collection("maps").add(mapWithUser)
                    .addOnSuccessListener { Log.i(TAG, "Successfully added map") }
                    .addOnFailureListener { e -> Log.e(TAG, "Error adding map", e) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = Firebase.firestore
        auth = Firebase.auth
        
        // If not logged in, go back to Login (shouldn't happen with Manifest changes but good safety)
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        rvMaps = findViewById(R.id.rvMaps)
        fabCreateMap = findViewById(R.id.fabCreateMap)
        progressBar = findViewById(R.id.progressBar)
        llEmptyState = findViewById(R.id.llEmptyState)

        rvMaps.layoutManager = LinearLayoutManager(this)
        mapAdapter = MapsAdapter(this, userMaps, object : MapsAdapter.OnClickListener {
            override fun onItemClick(position: Int) {
                Log.i(TAG, "onItemClick $position")
                val intent = Intent(this@MainActivity, DisplayMapActivity::class.java)
                intent.putExtra(EXTRA_USER_MAP, userMaps[position])
                startActivity(intent)
            }

            override fun onItemLongClick(position: Int) {
                Log.i(TAG, "onItemLongClick $position")
                showDeleteDialog(position)
            }
        })
        rvMaps.adapter = mapAdapter

        // Listen for real-time updates from Firestore - FILTERED BY USER
        val currentUid = auth.currentUser?.uid ?: ""
        progressBar.visibility = android.view.View.VISIBLE
        db.collection("maps")
            .whereEqualTo("userId", currentUid)
            .addSnapshotListener { snapshot, e ->
                progressBar.visibility = android.view.View.GONE
                if (e != null) {
                    Log.w(TAG, "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val maps = snapshot.toObjects(UserMap::class.java)
                    userMaps.clear()
                    userMaps.addAll(maps)

                    if (userMaps.isEmpty()) {
                        llEmptyState.visibility = android.view.View.VISIBLE
                        // Add some dummy data to show them what's possible
                        userMaps.addAll(generateSampleData())
                    } else {
                        llEmptyState.visibility = android.view.View.GONE
                    }
                    mapAdapter.notifyDataSetChanged()
                }
            }

        fabCreateMap.setOnClickListener {
            Log.i(TAG, "tap on fab")
            showAlertDialogue()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.miLogout) {
            Log.i(TAG, "Logout")
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showDeleteDialog(position: Int) {
        val userMap = userMaps[position]
        AlertDialog.Builder(this)
            .setTitle("Delete '${userMap.title}'?")
            .setMessage("Are you sure you want to delete this map?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val docId = userMap.id
                if (docId != null) {
                    db.collection("maps").document(docId).delete()
                        .addOnSuccessListener { 
                            Log.i(TAG, "Successfully deleted map")
                            Toast.makeText(this, "Map deleted", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e -> Log.e(TAG, "Error deleting map", e) }
                } else {
                    Toast.makeText(this, "Sample maps cannot be deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun showAlertDialogue() {
        val mapFormView = LayoutInflater.from(this).inflate(R.layout.dialog_create_map, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Map title")
            .setView(mapFormView)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Ok", null)
            .show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val title = mapFormView.findViewById<EditText>(R.id.etTitle).text.toString()

            if (title.trim().isEmpty()) {
                Toast.makeText(this, "Map must have a non-empty title", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val intent = Intent(this@MainActivity, CreateMapActivity::class.java)
            intent.putExtra(EXTRA_MAP_TITLE, title)
            getMapResult.launch(intent)
            dialog.dismiss()
        }
    }

    private fun generateSampleData(): List<UserMap> {
        return listOf(
            UserMap(
                "Sample: Memories from University",
                listOf(
                    Place("Branner Hall", "Best dorm at Stanford", 37.426, -122.163),
                    Place("Gates CS building", "Many long nights in this basement", 37.430, -122.173),
                    Place("Pinkberry", "First date with my wife", 37.444, -122.170)
                )
            ),
            UserMap(
                "Sample: January vacation planning!",
                listOf(
                    Place("Tokyo", "Overnight layover", 35.67, 139.65),
                    Place("Ranchi", "Family visit + wedding!", 23.34, 85.31),
                    Place("Singapore", "Inspired by \"Crazy Rich Asians\"", 1.35, 103.82)
                )
            )
        )
    }
}
