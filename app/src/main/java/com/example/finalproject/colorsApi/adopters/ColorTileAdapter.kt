package com.example.finalproject.colorsApi.adopters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalproject.R
import com.example.finalproject.colorsApi.models.ColorTile

class ColorTileAdapter(
    private val onClick: (ColorTile) -> Unit
) : ListAdapter<ColorTile, ColorTileAdapter.VH>(DIFF) {

    private var selectedPos = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_color_tile, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), position == selectedPos)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val iv: ImageView = view.findViewById(R.id.ivColor)

        init {
            itemView.setOnClickListener {
                val pos = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener
                val item = getItem(pos)
                val old = selectedPos
                selectedPos = pos
                notifyItemChanged(old)
                notifyItemChanged(selectedPos)
                onClick(item)
            }
        }

        fun bind(item: ColorTile, selected: Boolean) {
            // Fill circle with solid or gradient
            iv.background = itemView.context.getDrawable(R.drawable.bg_color_tile)
            iv.isSelected = selected

            val drawable = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                if (item.isGradient) {
                    intArrayOf(Color.parseColor(item.hex), Color.parseColor(item.hex2 ?: item.hex))
                } else {
                    intArrayOf(Color.parseColor(item.hex), Color.parseColor(item.hex))
                }
            )
            drawable.shape = GradientDrawable.OVAL
            iv.setImageDrawable(drawable)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ColorTile>() {
            override fun areItemsTheSame(o: ColorTile, n: ColorTile) = o.hex == n.hex && o.hex2 == n.hex2
            override fun areContentsTheSame(o: ColorTile, n: ColorTile) = o == n
        }
    }
}
