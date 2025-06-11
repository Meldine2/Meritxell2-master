package com.example.meritxell

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.FrameLayout // Import FrameLayout for your custom button
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class HomeFragment : Fragment() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuButton: Button
    private lateinit var btnAdopt: LinearLayout
    private lateinit var btnDonation: LinearLayout
    private lateinit var btnInbox: LinearLayout
    private lateinit var btnLegalAiAssist: FrameLayout // Changed name to reflect its new purpose

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        drawerLayout = requireActivity().findViewById(R.id.drawer_layout)
        menuButton = view.findViewById(R.id.menuButton)

        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        try {
            btnAdopt = view.findViewById(R.id.adopt_button_footer) ?: throw Exception("adopt_button_footer not found")
            btnDonation = view.findViewById(R.id.donation_button_footer) ?: throw Exception("donation_button_footer not found") 
            btnInbox = view.findViewById(R.id.inbox_button_footer) ?: throw Exception("inbox_button_footer not found")

            // Initialize the FrameLayout for the AI Legal Assist button
            // It's still using the ID 'fabChat' from your XML, but its purpose is now "AI Legal Assist"
            btnLegalAiAssist = view.findViewById(R.id.fabChat) ?: throw Exception("fabChat not found")
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error initializing views: ${e.message}")
            return view
        }
        btnLegalAiAssist.setOnClickListener {
            // Launch the ChatHead activity when the "AI Legal Assist" button is clicked
            startActivity(Intent(requireActivity(), ChatHead::class.java))
        }

        btnAdopt.setOnClickListener {
            getUserRole { role ->
                if (role == "admin") {
                    startActivity(Intent(requireActivity(), AdminAdoptActivity::class.java))
                } else {
                    startActivity(Intent(requireActivity(), UserAdoptActivity::class.java))
                }
            }
        }

        btnDonation.setOnClickListener {
            getUserRole { role ->
                if (role == "admin") {
                    startActivity(Intent(requireActivity(), AdminDonationHubActivity::class.java))
                } else {
                    startActivity(Intent(requireActivity(), UserDonationHubActivity::class.java))
                }
            }
        }

        btnInbox.setOnClickListener {
            startActivity(Intent(requireActivity(), InboxActivity::class.java))
        }

        return view
    }

    private fun getUserRole(callback: (String) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            callback("user")
            return
        }
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                val role = document.getString("role") ?: "user"
                callback(role)
            }
            .addOnFailureListener {
                callback("user")
            }
    }
}
