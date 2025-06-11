package com.example.meritxell

import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HistoryDetailActivity : AppCompatActivity() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    private lateinit var tvTitle: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvTimestamp: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnBack: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history_detail)
        
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        
        initViews()
        loadRecordDetails()
    }
    
    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvCategory = findViewById(R.id.tvCategory)
        tvDescription = findViewById(R.id.tvDescription)
        tvTimestamp = findViewById(R.id.tvTimestamp)
        tvStatus = findViewById(R.id.tvStatus)
        btnBack = findViewById(R.id.btnBack)
        
        btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun loadRecordDetails() {
        val recordId = intent.getStringExtra("recordId") ?: ""
        val category = intent.getStringExtra("category") ?: ""
        val userId = intent.getStringExtra("userId") ?: ""
        
        // For now, just display the basic info
        tvTitle.text = "Record ID: $recordId"
        tvCategory.text = "Category: $category"
        tvDescription.text = "User ID: $userId"
        tvTimestamp.text = "Timestamp: Loading..."
        tvStatus.text = "Status: Loading..."
    }
} 
 