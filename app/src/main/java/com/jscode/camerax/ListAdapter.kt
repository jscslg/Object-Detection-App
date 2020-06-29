package com.jscode.camerax

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jscode.camerax.databinding.ItemBinding
import com.jscode.camerax.viewModel.Recognition

class ListAdapter() :
    ListAdapter<Recognition, com.jscode.camerax.ListAdapter.RecognitionViewHolder>(RecognitionDiffUtil()) {

    /**
     * Inflating the ViewHolder with recognition_item layout and data binding
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecognitionViewHolder {
        return RecognitionViewHolder.from(parent)
    }

    // Binding the data fields to the RecognitionViewHolder
    override fun onBindViewHolder(holder: RecognitionViewHolder, position: Int) {
        holder.bindTo(getItem(position))
    }
    class RecognitionViewHolder(private val binding: ItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        companion object{
            fun from(parent: ViewGroup):RecognitionViewHolder{
                val inflater = LayoutInflater.from(parent.context)
                val binding = ItemBinding.inflate(inflater, parent, false)
                return RecognitionViewHolder(binding)
            }
        }
        fun bindTo(recognition: Recognition) {
            binding.rec = recognition
            binding.executePendingBindings()
        }
    }

    private class RecognitionDiffUtil : DiffUtil.ItemCallback<Recognition>() {
        override fun areItemsTheSame(oldItem: Recognition, newItem: Recognition): Boolean {
            return oldItem.label == newItem.label
        }

        override fun areContentsTheSame(oldItem: Recognition, newItem: Recognition): Boolean {
            return oldItem.confidence == newItem.confidence
        }
    }
}

