package com.example.finalproject.ui.register

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.finalproject.R
import com.example.finalproject.data.models.BusinessType
import com.example.finalproject.databinding.BussinessItemTypeBinding



class BusinessTypeAdapter(
    private val onItemClick:(String)-> Unit
): ListAdapter<BusinessType, BusinessTypeAdapter.BusinessTypeViewHolder>(DIFF_CALLBACK) {

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BusinessType>() {
            override fun areItemsTheSame(oldItem: BusinessType, newItem: BusinessType): Boolean {
                return oldItem.title== newItem.title
            }

            override fun areContentsTheSame(oldItem: BusinessType, newItem: BusinessType): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BusinessTypeViewHolder {
        val binding = BussinessItemTypeBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return BusinessTypeViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: BusinessTypeViewHolder,
        position: Int
    ) {
        val item = currentList[position]
        holder.bindView(item)
    }

    inner class BusinessTypeViewHolder(val binding: BussinessItemTypeBinding): RecyclerView.ViewHolder(binding.root){

        fun bindView(businessType: BusinessType){
            Glide.with(
                binding.root.context
            ).load(
                businessType.iconPath
            ).into(binding.icon)
            binding.title.text = businessType.title

            binding.root.setOnClickListener {
                //onItemClick.invoke(businessType.title)
                currentList.forEach { it.isSelected = false }

                // Select the clicked one
                businessType.isSelected = true

                // Update whole list
                notifyDataSetChanged()

                onItemClick.invoke(businessType.title)
            }

            if (businessType.isSelected) {
                binding.root.setBackgroundResource(R.drawable.ic_selected_bg)
            } else {
                binding.root.setBackgroundResource(R.drawable.item_bg)
            }
        }
    }

}

