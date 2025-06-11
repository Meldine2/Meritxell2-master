package com.example.meritxell

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class ChildStatusActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_status)

        supportActionBar?.title = null
        supportActionBar?.hide()

        // Hide the action bar for this activity
        supportActionBar?.hide()

        // Handle "See More" buttons for each child
        setupSeeMoreButton(R.id.seeMoreButton1, R.id.extraInfoLayout1)
        setupSeeMoreButton(R.id.seeMoreButton2, R.id.extraInfoLayout2)
        setupSeeMoreButton(R.id.seeMoreButton3, R.id.extraInfoLayout3)
        // Repeat for additional children as needed

        // Handle the back button functionality
        val backButton: ImageView = findViewById(R.id.btnBack)
        backButton.setOnClickListener {
            onBackPressed()  // This will navigate back to the previous activity
        }
    }

    private fun setupSeeMoreButton(buttonId: Int, layoutId: Int) {
        val seeMoreButton: Button = findViewById(buttonId)
        val extraInfoLayout: LinearLayout = findViewById(layoutId)

        // Handle See More button click
        seeMoreButton.setOnClickListener {
            // Toggle visibility of extra info
            if (extraInfoLayout.visibility == View.GONE) {
                extraInfoLayout.visibility = View.VISIBLE
                seeMoreButton.text = "See less..." // Change button text to "See less"
            } else {
                extraInfoLayout.visibility = View.GONE
                seeMoreButton.text = "See more..." // Change button text back to "See more"
            }
        }
    }
}
