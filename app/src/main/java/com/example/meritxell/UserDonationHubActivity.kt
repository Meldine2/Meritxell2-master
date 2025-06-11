package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class UserDonationHubActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_donation_hub)

        supportActionBar?.title = null
        supportActionBar?.hide()


        // Back Button
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        // Donate Money Button
        val btnDonateMoney = findViewById<Button>(R.id.btnDonateMoney)
        btnDonateMoney.setOnClickListener {
            startActivity(Intent(this, MoneyDonationActivity::class.java))
        }

        // Food
        val foodLayout = findViewById<LinearLayout>(R.id.btnFood)
        foodLayout.setOnClickListener {
            startActivity(Intent(this, FoodDonationActivity::class.java))
        }

        // Clothes
        val clothesLayout = findViewById<LinearLayout>(R.id.btnClothes)
        clothesLayout.setOnClickListener {
            startActivity(Intent(this, ClothesDonationActivity::class.java))
        }

        // Education (General Donation)
        val educationLayout = findViewById<LinearLayout>(R.id.btnEducation)
        educationLayout.setOnClickListener {
            startActivity(Intent(this, EducationDonationActivity::class.java))
        }

        // Toys
        val toysLayout = findViewById<LinearLayout>(R.id.btnToys)
        toysLayout.setOnClickListener {
            startActivity(Intent(this, ToysDonationActivity::class.java))
        }

        // Track Donation
        val btnTrackDonation = findViewById<Button>(R.id.btnTrackDonation)
        btnTrackDonation.setOnClickListener {
            startActivity(Intent(this, TrackDonationActivity::class.java))
        }

        // Sponsor a Child: Education & Medicine
        val sponsorEducationBtn = findViewById<Button>(R.id.btnSponsorEducation)
        sponsorEducationBtn.setOnClickListener {
            startActivity(Intent(this, SponsorEducationActivity::class.java))
        }

        val sponsorMedicineBtn = findViewById<Button>(R.id.btnSponsorMedicine)
        sponsorMedicineBtn.setOnClickListener {
            startActivity(Intent(this, SponsorMedicineActivity::class.java))
        }
    }
}
