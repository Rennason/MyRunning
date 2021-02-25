package com.sdeoliveira.maps

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

const val EXTRA_MESSAGE = "com.example.minhasorte.MESSAGE"

class User : AppCompatActivity() {


    //val nameEdit = findViewById<EditText>(R.id.nameEdit)
   // val weightEdit = findViewById<EditText>(R.id.weight_user)
   // val heightEdit = findViewById<EditText>(R.id.height_user)
    //val ageEdit = findViewById<EditText>(R.id.age_user)




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Maps)
        setContentView(R.layout.activity_user)

        val btnSave = findViewById<Button>(R.id.btnSave)

        btnSave.setOnClickListener(){
            sendMessage(it, "ABC")

//           val intent = Intent(this,
//                    MainActivity::class.java)
//            startActivity(intent)
        }
    }

    private fun sendMessage(view: View, message : String){
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("MESSAGE1", message)
        }
        startActivity(intent)
    }


}
