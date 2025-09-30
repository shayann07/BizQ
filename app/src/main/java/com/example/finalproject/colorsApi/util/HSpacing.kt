package com.example.finalproject.colorsApi.util

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class HSpacing(private val spacePx: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, v: View, p: RecyclerView, s: RecyclerView.State) {
        outRect.right = spacePx
    }
}
