package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class UserMatchingActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    // UI elements for Terms and Conditions section
    private lateinit var termsAndConditionsSection: LinearLayout
    private lateinit var termsCheckbox: CheckBox
    private lateinit var btnGetStarted: Button
    private lateinit var btnViewMatching: Button

    // UI elements for Matching Form section
    private lateinit var spinnerGender: Spinner
    private lateinit var spinnerSkinColor: Spinner
    private lateinit var spinnerCharacteristics: Spinner
    private lateinit var spinnerPreferredSize: Spinner
    private lateinit var spinnerPreferredAge: Spinner
    private lateinit var editTextOtherPreferences: EditText
    private lateinit var buttonSubmitPreferences: Button
    private lateinit var matchingFormSection: LinearLayout

    // UI elements for Match Result section (will largely be unused with new flow)
    private lateinit var matchResultSection: LinearLayout
    private lateinit var textViewMatchMessage: TextView
    private lateinit var textViewMatchedChildDetails: TextView
    private lateinit var buttonViewChildProfile: Button
    private lateinit var buttonRequestAdminReview: Button
    private lateinit var backButton: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_matching)
        // Iitialize Firebase
        auth = FirebaseAuth.getInstance()
        db = Firebase.firestore
        supportActionBar?.hide()

        // Initialize UI components
        termsAndConditionsSection = findViewById(R.id.terms_and_conditions_section)
        termsCheckbox = findViewById(R.id.termsCheckbox)
        btnGetStarted = findViewById(R.id.btnGetStarted)
        btnViewMatching = findViewById(R.id.btnViewMatching)

        matchingFormSection = findViewById(R.id.matching_form_section)
        spinnerGender = findViewById(R.id.spinner_gender)
        spinnerSkinColor = findViewById(R.id.spinner_skin_color)
        spinnerCharacteristics = findViewById(R.id.spinner_characteristics)
        spinnerPreferredSize = findViewById(R.id.spinner_preferred_size)
        spinnerPreferredAge = findViewById(R.id.spinner_preferred_age)
        editTextOtherPreferences = findViewById(R.id.edit_text_other_preferences)
        buttonSubmitPreferences = findViewById(R.id.button_submit_preferences)

        // Initialize NEW UI components for match result
        matchResultSection = findViewById(R.id.match_result_section)
        textViewMatchMessage = findViewById(R.id.text_view_match_message)
        textViewMatchedChildDetails = findViewById(R.id.text_view_matched_child_details)
        buttonViewChildProfile = findViewById(R.id.button_view_child_profile)
        buttonRequestAdminReview = findViewById(R.id.button_request_admin_review)

        backButton = findViewById(R.id.back_button)
        setupSpinners()
        setupListeners()

        // Initially show terms and conditions, hide form and match result
        termsAndConditionsSection.visibility = View.VISIBLE
        matchingFormSection.visibility = View.GONE
        matchResultSection.visibility = View.GONE
    }

    private fun setupSpinners() {
        ArrayAdapter.createFromResource(this, R.array.gender_options, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerGender.adapter = adapter
            }

        ArrayAdapter.createFromResource(this, R.array.skin_color_options, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerSkinColor.adapter = adapter
            }

        ArrayAdapter.createFromResource(this, R.array.characteristics_options, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerCharacteristics.adapter = adapter
            }

        ArrayAdapter.createFromResource(this, R.array.preferred_size_options, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerPreferredSize.adapter = adapter
            }

        ArrayAdapter.createFromResource(this, R.array.preferred_age_options, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerPreferredAge.adapter = adapter
            }
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            onBackPressed()
        }

        btnGetStarted.setOnClickListener {
            if (termsCheckbox.isChecked) {
                termsAndConditionsSection.visibility = View.GONE
                matchingFormSection.visibility = View.VISIBLE
            } else {
                Toast.makeText(this, "Please accept the Terms and Conditions to proceed.", Toast.LENGTH_SHORT).show()
            }
        }

        btnViewMatching.setOnClickListener {
            val intent = Intent(this, ViewMatchingStatusActivity::class.java)
            startActivity(intent)
        }

        buttonSubmitPreferences.setOnClickListener {
            submitMatchingPreferences()
        }
    }

    private fun submitMatchingPreferences() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You need to be logged in to submit preferences.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val userId = currentUser.uid

        val selectedGender = spinnerGender.selectedItem?.toString()
        val selectedSkinColor = spinnerSkinColor.selectedItem?.toString()
        val selectedCharacteristics = spinnerCharacteristics.selectedItem?.toString()
        val selectedPreferredSize = spinnerPreferredSize.selectedItem?.toString()
        val selectedPreferredAge = spinnerPreferredAge.selectedItem?.toString()
        val otherPreferences = editTextOtherPreferences.text.toString().trim()

        val genderPlaceholder = resources.getStringArray(R.array.gender_options)[0]
        val skinColorPlaceholder = resources.getStringArray(R.array.skin_color_options)[0]
        val characteristicsPlaceholder = resources.getStringArray(R.array.characteristics_options)[0]
        val preferredSizePlaceholder = resources.getStringArray(R.array.preferred_size_options)[0]
        val preferredAgePlaceholder = resources.getStringArray(R.array.preferred_age_options)[0]

        if (selectedGender == null || selectedGender == genderPlaceholder ||
            selectedSkinColor == null || selectedSkinColor == skinColorPlaceholder ||
            selectedCharacteristics == null || selectedCharacteristics == characteristicsPlaceholder ||
            selectedPreferredSize == null || selectedPreferredSize == preferredSizePlaceholder ||
            selectedPreferredAge == null || selectedPreferredAge == preferredAgePlaceholder) {
            Toast.makeText(this, "Please select all required preferences.", Toast.LENGTH_SHORT).show()
            return
        }

        buttonSubmitPreferences.isEnabled = false

        // Fetch the username before submitting the preferences
        db.collection("users").document(userId).get()
            .addOnSuccessListener { userDocument ->
                val username = userDocument.getString("username") // Assuming "username" field in your user document
                if (username != null) {
                    val matchingRequest: HashMap<String, Any> = hashMapOf(
                        "senderId" to userId,
                        "senderUsername" to username, // Add the username here
                        "genderPreference" to selectedGender,
                        "skinColorPreference" to selectedSkinColor,
                        "characteristicsPreference" to selectedCharacteristics,
                        "preferredSize" to selectedPreferredSize,
                        "preferredAge" to selectedPreferredAge,
                        "otherPreferences" to otherPreferences,
                        "requestTimestamp" to System.currentTimeMillis(),
                        "status" to "pending" // Initial status is always pending
                    )
                    findAndSubmitMatch(userId, matchingRequest)
                } else {
                    Toast.makeText(this, "User data not found. Cannot submit preferences.", Toast.LENGTH_SHORT).show()
                    buttonSubmitPreferences.isEnabled = true
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching user data: ${e.message}", Toast.LENGTH_LONG).show()
                buttonSubmitPreferences.isEnabled = true
                e.printStackTrace()
            }
    }

    private fun findAndSubmitMatch(userId: String, userPreferences: HashMap<String, Any>) {
        db.collection("children")
            .get()
            .addOnSuccessListener { querySnapshot ->
                var bestMatch: Map<String, Any>? = null
                var maxMatches = 0
                val matchedCriteria = mutableListOf<String>()
                var bestMatchId: String? = null

                for (document in querySnapshot.documents) {
                    val child = document.data
                    if (child != null && child["status"] == "Available") { // Only consider available children
                        var currentMatches = 0
                        val currentMatchedCriteria = mutableListOf<String>()

                        val childGender = child["gender"]?.toString()
                        val childSkinColor = child["skinColor"]?.toString()
                        val childCharacteristics = child["characteristics"]?.toString()
                        val childSize = child["size"]?.toString()
                        val childAge = child["age"]?.toString()

                        val userPreferredGender = userPreferences["genderPreference"]?.toString()
                        if (userPreferredGender != null && userPreferredGender != resources.getStringArray(R.array.gender_options)[0]) {
                            if (userPreferredGender == "Any" || userPreferredGender == childGender) {
                                currentMatches++
                                currentMatchedCriteria.add("Gender: ${childGender ?: "N/A"}")
                            }
                        }

                        val userPreferredSkinColor = userPreferences["skinColorPreference"]?.toString()
                        if (userPreferredSkinColor != null && userPreferredSkinColor != resources.getStringArray(R.array.skin_color_options)[0]) {
                            if (userPreferredSkinColor == "Any" || userPreferredSkinColor == childSkinColor) {
                                currentMatches++
                                currentMatchedCriteria.add("Skin Color: ${childSkinColor ?: "N/A"}")
                            }
                        }

                        val userPreferredCharacteristics = userPreferences["characteristicsPreference"]?.toString()
                        if (userPreferredCharacteristics != null && userPreferredCharacteristics != resources.getStringArray(R.array.characteristics_options)[0]) {
                            if (userPreferredCharacteristics == "Any" || userPreferredCharacteristics == childCharacteristics) {
                                currentMatches++
                                currentMatchedCriteria.add("Characteristics: ${childCharacteristics ?: "N/A"}")
                            }
                        }

                        val userPreferredSize = userPreferences["preferredSize"]?.toString()
                        if (userPreferredSize != null && userPreferredSize != resources.getStringArray(R.array.preferred_size_options)[0]) {
                            if (userPreferredSize == "Any" || userPreferredSize == childSize) {
                                currentMatches++
                                currentMatchedCriteria.add("Size: ${childSize ?: "N/A"}")
                            }
                        }

                        val userPreferredAge = userPreferences["preferredAge"]?.toString()
                        if (userPreferredAge != null && userPreferredAge != resources.getStringArray(R.array.preferred_age_options)[0]) {
                            if (userPreferredAge == "Any" || userPreferredAge == childAge) {
                                currentMatches++
                                currentMatchedCriteria.add("Age: ${childAge ?: "N/A"}")
                            }
                        }

                        if (currentMatches > maxMatches) {
                            maxMatches = currentMatches
                            bestMatch = child
                            bestMatchId = document.id
                            matchedCriteria.clear()
                            matchedCriteria.addAll(currentMatchedCriteria)
                        }
                    }
                }

                if (bestMatch != null && maxMatches >= 3) { // Match found with 3 or more preferences
                    val childId = bestMatchId

                    userPreferences["receiverId"] = childId ?: "N/A"
                    userPreferences["matchedChildDetails"] = bestMatch

                    db.collection("matching_preferences").add(userPreferences)
                        .addOnSuccessListener { documentReference ->
                            Toast.makeText(this, "Match found and submitted! Awaiting acceptance.", Toast.LENGTH_LONG).show()
                            childId?.let { db.collection("children").document(it).update("status", "Pending Match") }

                            val intent = Intent(this, ViewMatchingStatusActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error saving match: ${e.message}", Toast.LENGTH_LONG).show()
                            buttonSubmitPreferences.isEnabled = true
                        }
                } else {
                    // No match found or not enough criteria met (less than 3 matches)
                    Toast.makeText(this, "No match found. Please try again with different preferences.", Toast.LENGTH_LONG).show()
                    buttonSubmitPreferences.isEnabled = true // Re-enable button so user can try again
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching children data: ${e.message}", Toast.LENGTH_LONG).show()
                buttonSubmitPreferences.isEnabled = true
                e.printStackTrace()
            }
    }

    private fun showMatchResult(message: String, matchedChildId: String?, childDetails: Map<String, Any>?) {
        // This function is kept but will effectively not be reached
        // due to the immediate redirects in findAndSubmitMatch.
        matchingFormSection.visibility = View.GONE
        termsAndConditionsSection.visibility = View.GONE
        matchResultSection.visibility = View.VISIBLE

        textViewMatchMessage.text = message

        if (childDetails != null) {
            val childName = childDetails["name"]?.toString() ?: "N/A"
            val childGender = childDetails["gender"]?.toString() ?: "N/A"
            val childAge = childDetails["age"]?.toString() ?: "N/A"
            val childCharacteristics = childDetails["characteristics"]?.toString() ?: "N/A"
            val childSize = childDetails["size"]?.toString() ?: "N/A"
            val childSkinColor = childDetails["skinColor"]?.toString() ?: "N/A"

            textViewMatchedChildDetails.text = """
Name: $childName
Gender: $childGender
Age: $childAge
Characteristics: $childCharacteristics
Size: $childSize
Skin Color: $childSkinColor
""".trimIndent()

            buttonViewChildProfile.visibility = View.VISIBLE
            buttonViewChildProfile.setOnClickListener {
                Toast.makeText(this, "Viewing profile for $childName (Feature coming soon!)", Toast.LENGTH_SHORT).show()
            }
            buttonRequestAdminReview.visibility = View.VISIBLE
            buttonRequestAdminReview.setOnClickListener {
                val intent = Intent(this, ViewMatchingStatusActivity::class.java)
                startActivity(intent)
                finish()
            }

        } else {
            textViewMatchedChildDetails.text = "Error: Child details not found."
            buttonViewChildProfile.visibility = View.GONE
            buttonRequestAdminReview.visibility = View.VISIBLE
        }
        buttonSubmitPreferences.isEnabled = true
    }
}