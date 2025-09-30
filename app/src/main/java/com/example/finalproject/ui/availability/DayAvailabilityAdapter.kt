package com.example.finalproject.ui.availability

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finalproject.data.models.WorkingHour
import com.example.finalproject.databinding.ItemDayAvailabilityBinding


class DayAvailabilityAdapter(
    private val onTimeClicked: (position: Int, isStart: Boolean) -> Unit,
    private val onSwitchChanged: (position: Int, enabled: Boolean) -> Unit
) : ListAdapter<WorkingHour, DayAvailabilityAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<WorkingHour>() {
        override fun areItemsTheSame(o: WorkingHour, n: WorkingHour) = o.dayName == n.dayName
        override fun areContentsTheSame(o: WorkingHour, n: WorkingHour) = o == n
    }

    inner class VH(val binding: ItemDayAvailabilityBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WorkingHour) = with(binding) {
            //tvDayName.text = item.dayName
            tvDayName.text = binding.root.context.getString(item.day.stringRes)

            switchEnable.setOnCheckedChangeListener(null)
            switchEnable.isChecked = item.isWorking
            switchEnable.setOnCheckedChangeListener { _, checked ->
                onSwitchChanged(bindingAdapterPosition, checked)
            }


            StartTimeTv.text = item.startTime
            EndTimeTv.text   = item.endTime

            // זמינות לפי ה־switch
            StartTimeTv.isEnabled = item.isWorking
            EndTimeTv.isEnabled   = item.isWorking

            // מאזינים לפתיחת picker
            StartTimeTv.setOnClickListener { onTimeClicked(bindingAdapterPosition, true) }
            EndTimeTv.setOnClickListener   { onTimeClicked(bindingAdapterPosition, false) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        return VH(ItemDayAvailabilityBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}