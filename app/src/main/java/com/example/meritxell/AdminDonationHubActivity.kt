package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.meritxell.data.Donation
import com.google.firebase.Timestamp // Import Firestore's Timestamp class
import com.google.firebase.firestore.FirebaseFirestore
import java.text.ParseException // Import ParseException
import java.text.SimpleDateFormat
import java.util.*

class AdminDonationHubActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var donationsContainerLayout: LinearLayout
    private lateinit var donationTypeSpinner: Spinner
    private lateinit var searchEditText: EditText

    private val donationList = mutableListOf<Donation>()

    // Formatter for the string timestamp from 'donations' collection
    // IMPORTANT: This pattern MUST EXACTLY match the string format of your timestamp in 'donations' collection.
    // Based on your images, it looks like "yyyy-MM-dd HH:mm:ss"
    private val stringTimestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    // Formatter for displaying the date in your UI
    private val displayDateFormatter = SimpleDateFormat("MMM dd,yyyy HH:mm", Locale.getDefault())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_donation_hub)

        supportActionBar?.title = null
        supportActionBar?.hide()

        firestore = FirebaseFirestore.getInstance()
        donationsContainerLayout = findViewById(R.id.donationsContainerLayout)
        donationTypeSpinner = findViewById(R.id.donationTypeSpinner)
        searchEditText = findViewById(R.id.searchEditText)
        val btnBack: ImageView = findViewById(R.id.btnBack)

        btnBack.setOnClickListener {
            onBackPressed()
        }

        setupDonationTypeSpinner()
        fetchDonations() // This will now only fetch from "donations" collection

        searchEditText.setOnKeyListener { _, _, _ ->
            filterDonations()
            false
        }
    }

    private fun setupDonationTypeSpinner() {
        // Include all donation types (both monetary and in-kind)
        val donationTypes = arrayOf(
            "All Donation Types",
            "Money Sponsorship",
            "Education Sponsorship",
            "Medicine Sponsorship",
            "Toys Donation",
            "Clothes Donation",
            "Food Donation",
            "Education Donation"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, donationTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        donationTypeSpinner.adapter = adapter

        donationTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterDonations()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun fetchDonations() {
        donationList.clear()

        // REQUIREMENT: Hide approved donations from admin main view
        // Only fetch donations that are NOT approved (pending, under review, etc.)
        firestore.collection("donations")
            .whereNotEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    try {
                        val donation = document.toObject(Donation::class.java)
                        donation?.let {
                            it.id = document.id // Ensure the ID is set from the document ID

                            val timestampObject = document.get("timestamp")

                            if (timestampObject is Timestamp) {
                                it.displayDate = timestampObject.toDate().let { date ->
                                    displayDateFormatter.format(date)
                                }
                            } else if (timestampObject is String) {
                                it.displayDate = try {
                                    val date = stringTimestampFormatter.parse(timestampObject)
                                    date?.let { displayDateFormatter.format(it) }
                                } catch (e: ParseException) {
                                    Log.e("AdminDonationHub", "Error parsing string timestamp for document ${document.id}: $timestampObject - ${e.message}")
                                    "Invalid Date Format"
                                }
                            } else {
                                it.displayDate = "N/A"
                            }
                            donationList.add(it)
                        }
                    } catch (e: Exception) {
                        Log.e("AdminDonationHub", "Error processing document ${document.id} in 'donations' collection: ${e.message}", e)
                    }
                }
                
                // Also fetch from in-kind donation collections (toys, clothes, food, education)
                fetchInKindDonations()
            }
            .addOnFailureListener { exception ->
                Log.e("AdminDonationHub", "Error fetching from 'donations' collection: ${exception.message}", exception)
                exception.printStackTrace()
            }
    }
    
    /**
     * Fetch in-kind donations that are not approved
     * REQUIREMENT: Hide approved donations from admin main view
     */
    private fun fetchInKindDonations() {
        val inKindCollections = listOf("toysdonation", "clothesdonation", "fooddonation", "educationdonation")
        var collectionsProcessed = 0
        
        for (collectionName in inKindCollections) {
            firestore.collection(collectionName)
                .whereNotEqualTo("status", "approved")
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        try {
                            val donation = document.toObject(Donation::class.java)
                            donation?.let {
                                it.id = document.id
                                
                                // Set donation type based on collection
                                it.donationType = when (collectionName) {
                                    "toysdonation" -> "Toys Donation"
                                    "clothesdonation" -> "Clothes Donation"
                                    "fooddonation" -> "Food Donation"
                                    "educationdonation" -> "Education Donation"
                                    else -> "In-Kind Donation"
                                }
                                
                                val timestampObject = document.get("timestamp")
                                if (timestampObject is Timestamp) {
                                    it.displayDate = timestampObject.toDate().let { date ->
                                        displayDateFormatter.format(date)
                                    }
                                } else {
                                    it.displayDate = "N/A"
                                }
                                donationList.add(it)
                            }
                        } catch (e: Exception) {
                            Log.e("AdminDonationHub", "Error processing document ${document.id} in '$collectionName' collection: ${e.message}", e)
                        }
                    }
                    
                    collectionsProcessed++
                    if (collectionsProcessed == inKindCollections.size) {
                        // All collections processed, now filter and display
                        filterDonations()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("AdminDonationHub", "Error fetching from '$collectionName' collection: ${exception.message}", exception)
                    collectionsProcessed++
                    if (collectionsProcessed == inKindCollections.size) {
                        filterDonations()
                    }
                }
            }
    }

    // Removed fetchSpecificDonations() entirely as it's no longer needed - GOOD!

    private fun filterDonations() {
        val selectedType = donationTypeSpinner.selectedItem.toString()
        val searchText = searchEditText.text.toString().toLowerCase(Locale.getDefault())

        val filteredList = donationList.filter { donation ->
            val matchesType = if (selectedType == "All Donation Types") {
                true
            } else {
                // Now only checks against donationType for the "donations" collection
                donation.donationType == selectedType
            }

            val matchesSearch = if (searchText.isBlank()) {
                true
            } else {
                // Search now only applies to fields relevant to "donations"
                // --- REMOVED "collectionName" and "fullname" checks from here ---
                donation.username?.toLowerCase(Locale.getDefault())?.contains(searchText) == true ||
                        donation.donationType?.toLowerCase(Locale.getDefault())?.contains(searchText) == true
            }
            matchesType && matchesSearch
        }.sortedByDescending { it.displayDate?.let { dateString ->
            try {
                displayDateFormatter.parse(dateString)
            } catch (e: ParseException) {
                Date(0) // Fallback for invalid dates
            }
        } }

        displayDonations(filteredList)
    }

    private fun displayDonations(donations: List<Donation>) {
        donationsContainerLayout.removeAllViews()

        val inflater = LayoutInflater.from(this)

        for (donation in donations) {
            val itemView = inflater.inflate(R.layout.item_admin_donation, donationsContainerLayout, false)

            val typeTextView: TextView = itemView.findViewById(R.id.donationTypeTextView)
            val nameTextView: TextView = itemView.findViewById(R.id.donorNameTextView)
            val statusTextView: TextView = itemView.findViewById(R.id.donationStatusTextView)
            val dateTextView: TextView = itemView.findViewById(R.id.donationDateTextView)

            // Now, always assume it's from the "donations" collection
            typeTextView.text = donation.donationType ?: "N/A"
            nameTextView.text = "Donor: ${donation.username ?: "N/A"}"
            statusTextView.text = "Status: ${donation.status ?: "N/A"}"
            dateTextView.text = "Date: ${donation.displayDate ?: "N/A"}"
            statusTextView.visibility = View.VISIBLE // Always visible now

            itemView.setOnClickListener {
                val intent = Intent(this, AdminDonationDetailActivity::class.java)
                intent.putExtra("donationId", donation.id)
                
                // Determine collection name based on donation type
                val targetCollection = when (donation.donationType) {
                    "Toys Donation" -> "toysdonation"
                    "Clothes Donation" -> "clothesdonation"
                    "Food Donation" -> "fooddonation"
                    "Education Donation" -> "educationdonation"
                    else -> "donations" // Money, Education, Medicine sponsorships
                }
                intent.putExtra("collectionName", targetCollection)
                intent.putExtra("viewerRole", "admin")
                startActivity(intent)
            }

            donationsContainerLayout.addView(itemView)
        }
    }

    override fun onResume() {
        super.onResume()
        fetchDonations() // Re-fetch on resume to ensure latest data
    }
}