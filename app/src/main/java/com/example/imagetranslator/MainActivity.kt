package com.example.imagetranslator

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var OCRandTranslationImageBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        OCRandTranslationImageBtn = findViewById(R.id.OCRandTranslationImageBtn)

        OCRandTranslationImageBtn.setOnClickListener {
            val intent = Intent(this@MainActivity, OCRandTranslationImage::class.java)
            startActivity(intent)
        }
    }
}