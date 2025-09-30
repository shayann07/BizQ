package com.example.finalproject.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalproject.databinding.ServiceLayoutBinding
import com.example.finalproject.data.models.Service

class ServicesAdapter(
    private val onEditClick: (Service) -> Unit,
    private val onDeleteClick: (Service) -> Unit
) : ListAdapter<Service, ServicesAdapter.ServiceViewHolder>(ServiceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val binding = ServiceLayoutBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ServiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ServiceViewHolder(
        private val binding: ServiceLayoutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service) {
            binding.apply {
                tvServiceName.text = service.name
                tvServiceDuration.text = "${service.durationMinutes} דקות"
                tvServicePrice.text = "₪${service.priceShekels}"

                // הגדרת לחיצות
                ivEditService.setOnClickListener { onEditClick(service) }
                ivDeleteService.setOnClickListener { onDeleteClick(service) }

                // לחיצה על הפריט עצמו פותחת עריכה
                root.setOnClickListener { onEditClick(service) }

                // שינוי אלפא אם השירות לא פעיל
                root.alpha = if (service.isActive) 1.0f else 0.6f
            }
        }
    }
}

class ServiceDiffCallback : DiffUtil.ItemCallback<Service>() {
    override fun areItemsTheSame(oldItem: Service, newItem: Service): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Service, newItem: Service): Boolean {
        return oldItem == newItem
    }
}