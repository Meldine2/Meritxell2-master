package com.example.meritxell

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.meritxell.data.Donation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackDonationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var llDonationsContainer: LinearLayout
    private lateinit var tvNoDonations: TextView
    private lateinit var btnBack: ImageView

    companion object {
        private const val TAG = "TrackDonationActivity"
        private const val DONATIONS_COLLECTION = "donations"
        // IMPORTANT: Ensure this matches the EXACT format of your string timestamps in Firestore!
        private const val FIRESTORE_STRING_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
        private const val DISPLAY_DATE_FORMAT = "MMM dd,yyyy HH:mm"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_donation)

        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initViews()
        setupListeners()
        fetchUserDonations()
    }

    private fun initViews() {
        llDonationsContainer = findViewById(R.id.llDonationsContainer)
        tvNoDonations = findViewById(R.id.tvNoDonations)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            onBackPressed()
        }
    }

    private fun fetchUserDonations() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to view your donations.", Toast.LENGTH_SHORT).show()
            tvNoDonations.text = "Please log in to view your donations."
            tvNoDonations.visibility = View.VISIBLE
            llDonationsContainer.visibility = View.GONE
            Log.d(TAG, "No user logged in. Displaying 'Please log in' message.")
            return
        }

        val userId = currentUser.uid
        Log.d(TAG, "Attempting to fetch donations for userId: $userId")

        firestore.collection(DONATIONS_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING) // Assuming string timestamps are sortable (e.g., YYYY-MM-DD)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "Listen failed to fetch donations.", e)
                    Toast.makeText(this, "Error loading donations: ${e.message}", Toast.LENGTH_SHORT).show()
                    tvNoDonations.text = "Error loading donations."
                    tvNoDonations.visibility = View.VISIBLE
                    llDonationsContainer.visibility = View.GONE
                    return@addSnapshotListener
                }

                llDonationsContainer.removeAllViews() // Clear existing views for fresh list

                if (snapshots != null && !snapshots.isEmpty) {
                    Log.d(TAG, "Found ${snapshots.size()} donation(s) for user.")
                    tvNoDonations.visibility = View.GONE
                    llDonationsContainer.visibility = View.VISIBLE

                    for (doc in snapshots.documents) {
                        Log.d(TAG, "Processing document ID: ${doc.id}")
                        val donationId = doc.id
                        val currentUserId = doc.getString("userId")
                        val username = doc.getString("username")
                        val donationType = doc.getString("donationType")
                        val amount = doc.getString("amount")
                        val receiptUrl = doc.getString("receiptUrl")
                        val status = doc.getString("status")
                        val stringTimestamp = doc.getString("timestamp") // Get as String
                        val proofOfDonationText = doc.getString("proofOfDonationText")
                        val proofOfDonationImageUrl = doc.getString("proofOfDonationImageUrl")

                        val donation = Donation(
                            id = donationId,
                            userId = currentUserId,
                            username = username,
                            donationType = donationType,
                            amount = amount,
                            receiptUrl = receiptUrl,
                            status = status,
                            timestamp = stringTimestamp, // Assign the String timestamp
                            proofOfDonationText = proofOfDonationText,
                            proofOfDonationImageUrl = proofOfDonationImageUrl
                        )
                        Log.d(TAG, "Created Donation object: $donation")
                        addDonationView(donation)
                    }
                } else {
                    Log.d(TAG, "No donations found for user.")
                    tvNoDonations.text = "No donations found yet."
                    tvNoDonations.visibility = View.VISIBLE
                    llDonationsContainer.visibility = View.GONE
                }
            }
    }

    private fun addDonationView(donation: Donation) {
        // Log to confirm addDonationView is called
        Log.d(TAG, "Adding donation view for ID: ${donation.id}, Type: ${donation.donationType}")

        val cardMarginVerticalPx = resources.getDimensionPixelSize(R.dimen.card_margin_vertical)
        val cardPaddingPx = resources.getDimensionPixelSize(R.dimen.card_padding)
        val textMarginSmallPx = resources.getDimensionPixelSize(R.dimen.text_margin_small)
        val textMarginMediumPx = resources.getDimensionPixelSize(R.dimen.text_margin_medium)
        val imageProofHeightPx = resources.getDimensionPixelSize(R.dimen.image_height_donation_proof)

        val cardView = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, cardMarginVerticalPx, 0, cardMarginVerticalPx)
            }
            radius = resources.getDimension(R.dimen.card_corner_radius)
            cardElevation = resources.getDimension(R.dimen.card_elevation)
        }

        val innerLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(cardPaddingPx, cardPaddingPx, cardPaddingPx, cardPaddingPx)
        }

        val tvType = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = textMarginSmallPx }
            text = "Donation Type: ${donation.donationType ?: "N/A"}"
            setTypeface(null, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }
        innerLayout.addView(tvType)

        val tvAmount = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = textMarginSmallPx }
            text = "Amount: ${donation.amount ?: "N/A"}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
        }
        innerLayout.addView(tvAmount)

        val tvDate = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = textMarginMediumPx }

            val displayDateString: String = donation.timestamp?.let { timestampStr ->
                try {
                    val inputFormatter = SimpleDateFormat(FIRESTORE_STRING_DATE_FORMAT, Locale.getDefault())
                    val date: Date? = inputFormatter.parse(timestampStr)
                    date?.let { parsedDate ->
                        val outputFormatter = SimpleDateFormat(DISPLAY_DATE_FORMAT, Locale.getDefault())
                        outputFormatter.format(parsedDate)
                    } ?: run {
                        Log.w(TAG, "Parsed date was null for timestamp string: $timestampStr")
                        "Invalid Date" // If parsing results in null date
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing string timestamp for display: $timestampStr - ${e.message}", e)
                    "Parsing Error" // Fallback if parsing fails
                }
            } ?: "N/A" // If donation.timestamp is null

            text = "Date: $displayDateString"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        }
        innerLayout.addView(tvDate)

        val tvStatus = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = textMarginMediumPx }
            text = "Status: ${donation.status ?: "N/A"}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
            when (donation.status?.lowercase(Locale.getDefault())) {
                "approved" -> setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                "rejected" -> setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                "pending" -> setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                else -> setTextColor(ContextCompat.getColor(context, android.R.color.black))
            }
        }
        innerLayout.addView(tvStatus)

        // Conditional section for approved status and admin's proof
        if (donation.status?.lowercase(Locale.getDefault()) == "approved") {
            Log.d(TAG, "Donation ${donation.id} is Accepted. Adding proof details.")
            val tvAcceptedMessage = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = textMarginMediumPx
                    bottomMargin = textMarginMediumPx
                }
                text = "Meritxell Accepted the donation, thank-you."
                setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                setTypeface(null, Typeface.ITALIC)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            innerLayout.addView(tvAcceptedMessage)

            if (!donation.proofOfDonationText.isNullOrBlank()) {
                val tvAdminProofText = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = textMarginMediumPx }
                    text = "Admin's Comment: ${donation.proofOfDonationText}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(ContextCompat.getColor(context, android.R.color.black))
                }
                innerLayout.addView(tvAdminProofText)
                Log.d(TAG, "Added admin proof text.")
            }

            if (!donation.proofOfDonationImageUrl.isNullOrBlank()) {
                val ivAdminProofImage = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        imageProofHeightPx
                    ).apply { bottomMargin = textMarginMediumPx }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    contentDescription = "Admin's Proof of Donation Image"
                }
                innerLayout.addView(ivAdminProofImage)

                // Load image using Glide
                Glide.with(this)
                    .load(donation.proofOfDonationImageUrl)
                    .placeholder(R.drawable.ic_image_placeholder) // Make sure this drawable exists
                    .error(R.drawable.ic_image_error)       // Make sure this drawable exists
                    .into(ivAdminProofImage)
                Log.d(TAG, "Added admin proof image with URL: ${donation.proofOfDonationImageUrl}")
            }
        }

        cardView.addView(innerLayout)
        llDonationsContainer.addView(cardView)
        Log.d(TAG, "Added CardView for donation ${donation.id} to container.")
    }
}