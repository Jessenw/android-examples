package com.jessenw.customdraganddropexample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    lateinit var recyclerView: RecyclerView
    private val images = arrayListOf(
        R.drawable.ic_ecce_homo_art_56dp,
        R.drawable.ic_ecce_homo_color_56dp,
        R.drawable.ic_ecce_homo_delete_bucket_56dp,
        R.drawable.ic_ecce_homo_delete_bucket_lid_56dp,
        R.drawable.ic_ecce_homo_filter_56dp,
        R.drawable.ic_ecce_homo_filter_adjustable_48dp,
        R.drawable.ic_ecce_homo_opacity_56dp,
        R.drawable.ic_ecce_homo_text_56dp,
        R.drawable.ic_logo_launch,
        R.drawable.ic_media_error,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recycler_view)

        val layoutManager = GridLayoutManager(this.applicationContext, 2)
        layoutManager.spanSizeLookup = object: GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                if (position > 3) {
                    return 2
                }
                return 1
            }
        }
        recyclerView.layoutManager = layoutManager

        val adapter = MyAdapter(images)
        recyclerView.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(MyItemTouchHelperCallback())
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}
