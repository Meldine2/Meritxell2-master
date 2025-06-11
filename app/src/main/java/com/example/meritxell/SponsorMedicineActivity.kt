package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SponsorMedicineActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sponsor_medicine)

        supportActionBar?.title = null
        supportActionBar?.hide()

        val donationEditText = findViewById<EditText>(R.id.etSponsorshipAmount)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnNext).setOnClickListener {
            val amount = donationEditText.text.toString().trim()
            if (amount.isBlank()) {
                Toast.makeText(this, "Please enter a sponsorship amount", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, GcashPaymentActivity::class.java)
                intent.putExtra("amount", amount)
                intent.putExtra("donationType", "Medicine Sponsorship")
                startActivity(intent)
            }
        }

        donationEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString()
                val cleanInput = input.replace(Regex("[^\\d.]"), "")

                val corrected = cleanInput.replaceFirst(".", "DOT_TEMP")
                    .replace(".", "")
                    .replace("DOT_TEMP", ".")

                if (input != corrected) {
                    donationEditText.setText(corrected)
                    donationEditText.setSelection(corrected.length)
                }
            }

            override fun afterTextChanged(s: Editable?) {} // CORRECTED LINE: Removed the extra 'fun'
        })
    }
}