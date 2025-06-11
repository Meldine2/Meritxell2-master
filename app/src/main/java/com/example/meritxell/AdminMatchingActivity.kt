package com.example.meritxell

import android.content.Intent // Import for Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.Locale
import android.util.TypedValue

class AdminMatchingActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var childrenContainer: LinearLayout
    private lateinit var usersContainer: LinearLayout
    private lateinit var matchedPairsContainer: LinearLayout
    private lateinit var addChildButton: Button
    private lateinit var activityBackButton: ImageView

    // Companion object for Intent extra key
    companion object {
        const val USER_UID = "userId" // Changed to "userId" for consistency with UserProfileActivity
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        setContentView(R.layout.activity_admin_matching)

        supportActionBar?.title = null
        supportActionBar?.hide()

        db = FirebaseFirestore.getInstance()

        childrenContainer = findViewById(R.id.childrenContainer)
        usersContainer = findViewById(R.id.usersContainer)
        matchedPairsContainer = findViewById(R.id.matchedPairsContainer)
        addChildButton = findViewById(R.id.addChildButton)
        activityBackButton = findViewById(R.id.back_button)

        fetchChildren()
        fetchUsers()
        fetchMatchedPairs()

        addChildButton.setOnClickListener {
            showAddChildDialog()
        }

        activityBackButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun fetchChildren() {
        db.collection("children")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("AdminMatching", "Listen failed.", e)
                    Toast.makeText(this, "Error fetching children: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                childrenContainer.removeAllViews()

                if (snapshots != null) {
                    for (doc in snapshots) {
                        val child = doc.toObject(Child::class.java).apply { id = doc.id }
                        child?.let { currentChild ->
                            val childItemView = LayoutInflater.from(this).inflate(R.layout.item_child_admin, childrenContainer, false)

                            val nameTextView: TextView = childItemView.findViewById(R.id.childNameTextView)
                            val detailsTextView: TextView = childItemView.findViewById(R.id.childDetailsTextView)
                            val statusTextView: TextView = childItemView.findViewById(R.id.childStatusTextView)
                            val descriptionTextView: TextView = childItemView.findViewById(R.id.childDescriptionTextView)
                            val dynamicInfoLayout: LinearLayout = childItemView.findViewById(R.id.dynamicChildInfoLayout)
                            val editButton: Button = childItemView.findViewById(R.id.editChildButton)
                            val deleteButton: Button = childItemView.findViewById(R.id.deleteChildButton)

                            nameTextView.text = currentChild.name
                            detailsTextView.text = "Age: ${currentChild.age}, Gender: ${currentChild.gender.ifEmpty { "N/A" }}, Skin: ${currentChild.skinColor.ifEmpty { "N/A" }}"
                            statusTextView.text = "Status: ${currentChild.status}"
                            descriptionTextView.text = "Description: ${currentChild.description.take(50)}${if (currentChild.description.length > 50) "..." else ""}"

                            dynamicInfoLayout.removeAllViews()
                            val specificDetailsText = StringBuilder()
                            if (currentChild.characteristics.isNotEmpty()) specificDetailsText.append("Char: ${currentChild.characteristics}")
                            if (currentChild.size.isNotEmpty()) {
                                if (specificDetailsText.isNotEmpty()) specificDetailsText.append(", ")
                                specificDetailsText.append("Size: ${currentChild.size}")
                            }

                            if (specificDetailsText.isNotEmpty()) {
                                val additionalDetailsTv = TextView(this).apply {
                                    text = specificDetailsText.toString()
                                    textSize = 14f
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        topMargin = 2.dpToPx(this@AdminMatchingActivity)
                                    }
                                }
                                dynamicInfoLayout.addView(additionalDetailsTv)
                            }

                            editButton.setOnClickListener { showEditChildDialog(currentChild) }
                            deleteButton.setOnClickListener { deleteChild(currentChild.id) }
                            childItemView.setOnClickListener { showChildDetailsDialog(currentChild) }

                            childrenContainer.addView(childItemView)
                        }
                    }
                }
            }
    }

    private fun fetchUsers() {
        db.collection("users")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("AdminMatching", "Listen failed.", e)
                    Toast.makeText(this, "Error fetching users: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                usersContainer.removeAllViews()

                if (snapshots != null) {
                    for (doc in snapshots) {
                        val uid = doc.id
                        val username = doc.getString("username")

                        if (username != null) {
                            // Creating a simple User data class instance for display purposes
                            // You might have a more comprehensive User data class in your project
                            val currentUser = User(uid = uid, username = username)
                            val userItemView = LayoutInflater.from(this).inflate(R.layout.item_user_admin, usersContainer, false)

                            val userNameTextView: TextView = userItemView.findViewById(R.id.userNameTextView)

                            userNameTextView.text = currentUser.username

                            // --- KEY CHANGE HERE: Launch UserProfileActivity with the clicked user's UID ---
                            userItemView.setOnClickListener {
                                Log.d("AdminMatching", "User ${currentUser.username} clicked. Opening profile for UID: ${currentUser.uid}")
                                val intent = Intent(this, UserProfileActivity::class.java)
                                intent.putExtra(USER_UID, currentUser.uid) // Pass the specific user's UID
                                startActivity(intent)
                            }
                            // -------------------------------------------------------------------------

                            usersContainer.addView(userItemView)
                        } else {
                            Log.w("AdminMatching", "User document ${doc.id} missing 'username' field.")
                        }
                    }
                }
            }
    }

    private fun fetchMatchedPairs() {
        // REQUIREMENT: "Admins can no longer see completed adoption processes in the main view"
        // Only show accepted matches that haven't been completed (not moved to history)
        db.collection("matching_preferences")
            .whereEqualTo("status", "accepted")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("AdminMatching", "Listen failed.", e)
                    Toast.makeText(this, "Error fetching matched pairs: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                matchedPairsContainer.removeAllViews()

                if (snapshots != null) {
                    var activeMatchesCount = 0
                    val totalDocuments = snapshots.documents.size // Get total documents to check for last one

                    if (totalDocuments == 0) {
                        // Handle case where there are no documents at all
                        runOnUiThread {
                            val noMatchesView = TextView(this).apply {
                                text = "No active matches found. Completed matches are in history."
                                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                                setPadding(16, 16, 16, 16)
                            }
                            matchedPairsContainer.addView(noMatchesView)
                        }
                    } else {
                        for (doc in snapshots) {
                            val matchedPreference = doc.toObject(MatchingPreference::class.java).apply { id = doc.id }
                            matchedPreference?.let { currentPreference ->
                                // Check if this match is still active (not completed/moved to history)
                                checkMatchingStatus(currentPreference.senderId) { isCompleted ->
                                    if (!isCompleted) {
                                        val matchedPairItemView = LayoutInflater.from(this).inflate(R.layout.item_matched_pair_admin, matchedPairsContainer, false)

                                        val matchedUserTextView: TextView = matchedPairItemView.findViewById(R.id.matchedUserTextView)
                                        val matchedChildTextView: TextView = matchedPairItemView.findViewById(R.id.matchedChildTextView)
                                        val cancelMatchingButton: Button = matchedPairItemView.findViewById(R.id.cancelMatchingButton)

                                        // Use senderUsername and childName directly from the data class
                                        matchedUserTextView.text = "User: ${currentPreference.senderUsername}"
                                        matchedChildTextView.text = "Matched Child: ${currentPreference.childName}"

                                        cancelMatchingButton.setOnClickListener {
                                            cancelMatch(currentPreference)
                                        }

                                        matchedPairItemView.setOnClickListener {
                                            showMatchingPreferenceDetailsDialog(currentPreference)
                                        }

                                        matchedPairsContainer.addView(matchedPairItemView)
                                        activeMatchesCount++
                                    }

                                    // This check ensures the message is added only once after all documents have been processed
                                    if (doc == snapshots.documents.last() && activeMatchesCount == 0) {
                                        runOnUiThread {
                                            val noMatchesView = TextView(this).apply {
                                                text = "No active matches found. Completed matches are in history."
                                                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                                                setPadding(16, 16, 16, 16)
                                            }
                                            matchedPairsContainer.addView(noMatchesView)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }

    /**
     * Check if matching process is completed (adoption process finished)
     * REQUIREMENT: Hide completed matches from admin main view
     */
    private fun checkMatchingStatus(userId: String, callback: (Boolean) -> Unit) {
        db.collection("adoption_progress").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val step10Status = document.getString("step10_status")
                    val isCompleted = step10Status == "completed"
                    callback(isCompleted)
                } else {
                    callback(false) // No document means not completed
                }
            }
            .addOnFailureListener {
                callback(false) // On error, assume not completed to be safe
            }
    }

    private fun cancelMatch(matchedPreference: MatchingPreference) {
        AlertDialog.Builder(this)
            .setTitle("Cancel Match")
            .setMessage("Are you sure you want to cancel the match between ${matchedPreference.senderUsername} and ${matchedPreference.childName}? This will permanently remove the match record and make the child available again.")
            .setPositiveButton("Yes, Cancel") { dialog, _ ->
                val batch = db.batch()

                val preferenceRef = db.collection("matching_preferences").document(matchedPreference.id)
                // CHANGE: Delete the document instead of updating its status
                batch.delete(preferenceRef)

                val childRef = db.collection("children").document(matchedPreference.receiverId)
                batch.update(childRef, "status", "Available")

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Match cancelled and removed successfully!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error cancelling match: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("AdminMatching", "Error cancelling match", e)
                    }
                dialog.dismiss()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showMatchingPreferenceDetailsDialog(preference: MatchingPreference) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_matching_preference_details, null)

        val senderNameTv: TextView = dialogView.findViewById(R.id.detailPrefSenderNameTv)
        val childNameTv: TextView = dialogView.findViewById(R.id.detailPrefChildNameTv)
        val statusTv: TextView = dialogView.findViewById(R.id.detailPrefStatusTv)
        val matchedPreferencesTv: TextView = dialogView.findViewById(R.id.detailMatchedPreferencesTv) // New TextView for matched preferences

        senderNameTv.text = "User: ${preference.senderUsername}"
        childNameTv.text = "Matched Child: ${preference.childName}"
        statusTv.text = "Status: ${preference.status}"

        // REVISION START: Display all child details, then highlight user's matched preferences
        val childDetailsStringBuilder = StringBuilder("Matched Child's Details:\n")
        preference.matchedChildDetails?.let { childDetails ->
            childDetails["name"]?.toString()?.let { childDetailsStringBuilder.append("- Name: $it\n") }
            childDetails["age"]?.toString()?.let { childDetailsStringBuilder.append("- Age: $it\n") }
            childDetails["gender"]?.toString()?.let { childDetailsStringBuilder.append("- Gender: $it\n") }
            childDetails["skinColor"]?.toString()?.let { childDetailsStringBuilder.append("- Skin Color: $it\n") }
            childDetails["characteristics"]?.toString()?.let { childDetailsStringBuilder.append("- Characteristics: $it\n") }
            childDetails["size"]?.toString()?.let { childDetailsStringBuilder.append("- Size: $it\n") }
            childDetails["description"]?.toString()?.let { childDetailsStringBuilder.append("- Description: $it\n") }
        }

        childDetailsStringBuilder.append("\nUser's Matched Preferences:\n") // Separator

        val userMatchedPreferences = StringBuilder()
        preference.matchedChildDetails?.let { childDetails ->
            val childGender = childDetails["gender"]?.toString()
            val childSkinColor = childDetails["skinColor"]?.toString()
            val childCharacteristics = childDetails["characteristics"]?.toString()
            val childSize = childDetails["size"]?.toString()
            val childAge = childDetails["age"]?.toString()

            if (preference.genderPreference == childGender && preference.genderPreference.isNotEmpty() && preference.genderPreference != "Any") {
                userMatchedPreferences.append("- Gender: ${preference.genderPreference}\n")
            }
            if (preference.skinColorPreference == childSkinColor && preference.skinColorPreference.isNotEmpty() && preference.skinColorPreference != "Any") {
                userMatchedPreferences.append("- Skin Color: ${preference.skinColorPreference}\n")
            }
            if (preference.characteristicsPreference == childCharacteristics && preference.characteristicsPreference.isNotEmpty() && preference.characteristicsPreference != "Any") {
                userMatchedPreferences.append("- Characteristics: ${preference.characteristicsPreference}\n")
            }
            if (preference.preferredSize == childSize && preference.preferredSize.isNotEmpty() && preference.preferredSize != "Any") {
                userMatchedPreferences.append("- Preferred Size: ${preference.preferredSize}\n")
            }
            if (preference.preferredAge == childAge && preference.preferredAge.isNotEmpty() && preference.preferredAge != "Any") {
                userMatchedPreferences.append("- Preferred Age: ${preference.preferredAge}\n")
            }
        }

        if (preference.otherPreferences.isNotEmpty()) {
            userMatchedPreferences.append("- Other Preferences: ${preference.otherPreferences}\n")
        }

        if (userMatchedPreferences.isEmpty()) {
            childDetailsStringBuilder.append("No specific user preferences matched with this child.")
        } else {
            childDetailsStringBuilder.append(userMatchedPreferences.toString())
        }

        matchedPreferencesTv.text = childDetailsStringBuilder.toString().trim()
        // REVISION END


        AlertDialog.Builder(this)
            .setTitle("Matching Preference Details")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showAddChildDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_child, null)
        val dialogBackButton = dialogView.findViewById<ImageView>(R.id.dialog_back_button)

        val nameEditText = dialogView.findViewById<EditText>(R.id.childNameEditText)
        val ageSpinner = dialogView.findViewById<Spinner>(R.id.childAgeSpinner)
        val characteristicsSpinner = dialogView.findViewById<Spinner>(R.id.childCharacteristicsSpinner)
        val genderSpinner = dialogView.findViewById<Spinner>(R.id.childGenderSpinner)
        val sizeSpinner = dialogView.findViewById<Spinner>(R.id.childSizeSpinner)
        val skinColorSpinner = dialogView.findViewById<Spinner>(R.id.childSkinColorSpinner)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.childDescriptionEditText)

        setupSpinner(ageSpinner, resources.getStringArray(R.array.preferred_age_options), "Select Age")
        setupSpinner(characteristicsSpinner, resources.getStringArray(R.array.characteristics_options), "Select Characteristics")
        setupSpinner(genderSpinner, resources.getStringArray(R.array.gender_options), "Select Gender")
        setupSpinner(sizeSpinner, resources.getStringArray(R.array.preferred_size_options), "Select Size")
        setupSpinner(skinColorSpinner, resources.getStringArray(R.array.skin_color_options), "Select Skin Color")

        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle(null)
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, _ ->
                val name = nameEditText.text.toString().trim()
                val age = ageSpinner.selectedItem.toString().takeIf { it != "Select Age" } ?: ""
                val characteristics = characteristicsSpinner.selectedItem.toString().takeIf { it != "Select Characteristics" } ?: ""
                val gender = genderSpinner.selectedItem.toString().takeIf { it != "Select Gender" } ?: ""
                val size = sizeSpinner.selectedItem.toString().takeIf { it != "Select Size" } ?: ""
                val skinColor = skinColorSpinner.selectedItem.toString().takeIf { it != "Select Skin Color" } ?: ""
                val description = descriptionEditText.text.toString().trim()

                if (name.isNotEmpty()) {
                    val newChild = Child(
                        name = name,
                        age = age,
                        characteristics = characteristics,
                        gender = gender,
                        size = size,
                        skinColor = skinColor,
                        description = description,
                        status = "Available",
                        timestampAdded = Timestamp.now()
                    )
                    db.collection("children").add(newChild)
                        .addOnSuccessListener { Toast.makeText(this, "Child added successfully!", Toast.LENGTH_SHORT).show() }
                        .addOnFailureListener { e -> Toast.makeText(this, "Error adding child: ${e.message}", Toast.LENGTH_LONG).show() }
                } else {
                    Toast.makeText(this, "Name cannot be empty.", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)

        val dialog = dialogBuilder.create()

        dialogBackButton?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditChildDialog(child: Child) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_child, null)
        val dialogBackButton = dialogView.findViewById<ImageView>(R.id.dialog_back_button)

        val nameEditText = dialogView.findViewById<EditText>(R.id.childNameEditText)
        val ageSpinner = dialogView.findViewById<Spinner>(R.id.childAgeSpinner)
        val characteristicsSpinner = dialogView.findViewById<Spinner>(R.id.childCharacteristicsSpinner)
        val genderSpinner = dialogView.findViewById<Spinner>(R.id.childGenderSpinner)
        val sizeSpinner = dialogView.findViewById<Spinner>(R.id.childSizeSpinner)
        val skinColorSpinner = dialogView.findViewById<Spinner>(R.id.childSkinColorSpinner)
        val descriptionEditText = dialogView.findViewById<EditText>(R.id.childDescriptionEditText)

        nameEditText.setText(child.name)
        descriptionEditText.setText(child.description)

        setupSpinner(ageSpinner, resources.getStringArray(R.array.preferred_age_options), child.age.ifEmpty { "Select Age" })
        setupSpinner(characteristicsSpinner, resources.getStringArray(R.array.characteristics_options), child.characteristics.ifEmpty { "Select Characteristics" })
        setupSpinner(genderSpinner, resources.getStringArray(R.array.gender_options), child.gender.ifEmpty { "Select Gender" })
        setupSpinner(sizeSpinner, resources.getStringArray(R.array.preferred_size_options), child.size.ifEmpty { "Select Size" })
        setupSpinner(skinColorSpinner, resources.getStringArray(R.array.skin_color_options), child.skinColor.ifEmpty { "Select Skin Color" })

        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle(null)
            .setView(dialogView)
            .setPositiveButton("Update") { dialog, _ ->
                val updatedName = nameEditText.text.toString().trim()
                val updatedAge = ageSpinner.selectedItem.toString().takeIf { it != "Select Age" } ?: ""
                val updatedCharacteristics = characteristicsSpinner.selectedItem.toString().takeIf { it != "Select Characteristics" } ?: ""
                val updatedGender = genderSpinner.selectedItem.toString().takeIf { it != "Select Gender" } ?: ""
                val updatedSize = sizeSpinner.selectedItem.toString().takeIf { it != "Select Size" } ?: ""
                val updatedSkinColor = skinColorSpinner.selectedItem.toString().takeIf { it != "Select Skin Color" } ?: ""
                val updatedDescription = descriptionEditText.text.toString().trim()

                val childUpdates = mutableMapOf<String, Any>(
                    "name" to updatedName,
                    "age" to updatedAge,
                    "characteristics" to updatedCharacteristics,
                    "gender" to updatedGender,
                    "size" to updatedSize,
                    "skinColor" to updatedSkinColor,
                    "description" to updatedDescription
                )

                db.collection("children").document(child.id).update(childUpdates)
                    .addOnSuccessListener { Toast.makeText(this, "Child updated successfully!", Toast.LENGTH_SHORT).show() }
                    .addOnFailureListener { e -> Toast.makeText(this, "Error updating child: ${e.message}", Toast.LENGTH_LONG).show() }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)

        val dialog = dialogBuilder.create()
        dialogBackButton?.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }


    private fun deleteChild(childId: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Child")
            .setMessage("Are you sure you want to delete this child permanently?")
            .setPositiveButton("Delete") { dialog, _ ->
                db.collection("children").document(childId).delete()
                    .addOnSuccessListener { Toast.makeText(this, "Child deleted successfully!", Toast.LENGTH_SHORT).show() }
                    .addOnFailureListener { e -> Toast.makeText(this, "Error deleting child: ${e.message}", Toast.LENGTH_LONG).show() }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChildDetailsDialog(child: Child) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_child_details, null)
        val nameTv: TextView = dialogView.findViewById(R.id.detailChildNameTv)
        val ageTv: TextView = dialogView.findViewById(R.id.detailChildAgeTv)
        val genderTv: TextView = dialogView.findViewById(R.id.detailChildGenderTv)
        val statusTv: TextView = dialogView.findViewById(R.id.detailChildStatusTv)
        val descriptionTv: TextView = dialogView.findViewById(R.id.detailChildDescriptionTv)
        val dynamicInfoLayout: LinearLayout = dialogView.findViewById(R.id.detailDynamicChildInfoLayout)
        val editButton: Button = dialogView.findViewById(R.id.detailEditChildButton)
        val deleteButton: Button = dialogView.findViewById(R.id.detailDeleteChildButton)

        nameTv.text = "Name: ${child.name}"
        ageTv.text = "Age: ${child.age}"
        genderTv.text = "Gender: ${child.gender.ifEmpty { "N/A" }}"
        statusTv.text = "Status: ${child.status}"
        descriptionTv.text = "Description:\n${child.description.ifEmpty { "N/A" }}"

        dynamicInfoLayout.removeAllViews()

        val skinColorTv = TextView(this).apply {
            text = "Skin Color: ${child.skinColor.ifEmpty { "N/A" }}"
            textSize = 16f
            setPadding(0, 4.dpToPx(this@AdminMatchingActivity), 0, 0)
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        dynamicInfoLayout.addView(skinColorTv)

        val characteristicsTv = TextView(this).apply {
            text = "Characteristics: ${child.characteristics.ifEmpty { "N/A" }}"
            textSize = 16f
            setPadding(0, 4.dpToPx(this@AdminMatchingActivity), 0, 0)
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        dynamicInfoLayout.addView(characteristicsTv)

        val sizeTv = TextView(this).apply {
            text = "Size: ${child.size.ifEmpty { "N/A" }}"
            textSize = 16f
            setPadding(0, 4.dpToPx(this@AdminMatchingActivity), 0, 0)
            setTextColor(ContextCompat.getColor(context, R.color.black))
        }
        dynamicInfoLayout.addView(sizeTv)

        editButton.setOnClickListener {
            (dialogView.parent as? AlertDialog)?.dismiss()
            showEditChildDialog(child)
        }
        deleteButton.setOnClickListener {
            (dialogView.parent as? AlertDialog)?.dismiss()
            deleteChild(child.id)
        }

        AlertDialog.Builder(this)
            .setTitle("Child Details")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun Int.dpToPx(context: android.content.Context): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics
    ).toInt()

    private fun setupSpinner(spinner: Spinner, options: Array<String>, defaultSelection: String) {
        val fullOptions = if (!options.contains(defaultSelection)) {
            arrayOf(defaultSelection) + options
        } else {
            options
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fullOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val selectionIndex = fullOptions.indexOf(defaultSelection)
        if (selectionIndex != -1) {
            spinner.setSelection(selectionIndex)
        }
    }

    // --- Data Classes (assuming these are in your project) ---
    // Make sure these data classes match your Firestore document structure
    data class Child(
        var id: String = "",
        val name: String = "",
        val age: String = "",
        val gender: String = "",
        val skinColor: String = "",
        val characteristics: String = "",
        val size: String = "",
        val description: String = "",
        val status: String = "", // e.g., "Available", "Pending", "Adopted"
        val timestampAdded: Timestamp? = null
    )

    data class User(
        var uid: String = "", // This holds the Firestore document ID (user's UID)
        val username: String = ""
        // Add other user fields if you display them in item_user_admin.xml
    )

    data class MatchingPreference(
        var id: String = "",
        val senderId: String = "",
        val senderUsername: String = "",
        val receiverId: String = "", // Child's ID
        val childName: String = "", // Child's Name
        val preferredAge: String = "",
        val genderPreference: String = "",
        val skinColorPreference: String = "",
        val characteristicsPreference: String = "",
        val preferredSize: String = "",
        val otherPreferences: String = "",
        val status: String = "", // e.g., "pending", "accepted", "rejected"
        val timestampSent: Timestamp? = null,
        val matchedChildDetails: Map<String, Any>? = null // Stores child details at time of match
    )
}