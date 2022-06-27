package com.example.recyclerviewitemtouchhelper

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val movies = Movie.movies

        val recyclerView = findViewById<RecyclerView>(R.id.movies_recyclerview)
        val adapter = MoviesAdapter(movies)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Attach ItemTouchHelper
        val movieItemTouchHelper = ItemTouchHelper(MovieItemTouchHelperCallback(movies))
        movieItemTouchHelper.attachToRecyclerView(recyclerView)
    }
}
