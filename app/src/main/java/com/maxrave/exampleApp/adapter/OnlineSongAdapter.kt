package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.maxrave.exampleApp.databinding.ItemOnlineSongBinding
import com.maxrave.exampleApp.model.OnlineSong

class OnlineSongAdapter(
    private var songs: List<OnlineSong>,
    private val onClick: (OnlineSong) -> Unit
) : RecyclerView.Adapter<OnlineSongAdapter.OnlineViewHolder>() {

    class OnlineViewHolder(val binding: ItemOnlineSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnlineViewHolder {
        val binding = ItemOnlineSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OnlineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OnlineViewHolder, position: Int) {
        val song = songs[position]
        holder.binding.apply {
            tvOnlineTitle.text = song.title
            tvOnlineChannel.text = song.artist
            
            Glide.with(root.context)
                .load(song.thumbnailUrl)
                .centerCrop()
                .into(ivThumbnail)

            root.setOnClickListener { onClick(song) }
        }
    }

    override fun getItemCount() = songs.size

    fun updateList(newList: List<OnlineSong>) {
        songs = newList
        notifyDataSetChanged()
    }
}
