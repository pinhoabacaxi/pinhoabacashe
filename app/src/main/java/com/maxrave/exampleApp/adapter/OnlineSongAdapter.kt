package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.maxrave.exampleApp.databinding.ItemOnlineSongBinding
import com.maxrave.exampleApp.model.OnlineSong

class OnlineSongAdapter(
    private val onItemClick: (OnlineSong) -> Unit
) : RecyclerView.Adapter<OnlineSongAdapter.OnlineViewHolder>() {

    private var songs: List<OnlineSong> = emptyList()

    class OnlineViewHolder(val binding: ItemOnlineSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnlineViewHolder {
        val binding = ItemOnlineSongBinding.inflate(
            LayoutInflater.from(parent.context), 
            parent, 
            false
        )
        return OnlineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OnlineViewHolder, position: Int) {
        val song = songs[position]
        holder.binding.apply {
            tvOnlineTitle.text = song.title
            tvOnlineChannel.text = song.author

            Glide.with(holder.itemView.context)
                .load(song.thumbnailUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .centerCrop()
                .into(ivThumbnail)

            root.setOnClickListener { onItemClick(song) }
        }
    }

    override fun getItemCount(): Int = songs.size

    fun updateList(newSongs: List<OnlineSong>) {
        this.songs = newSongs
        notifyDataSetChanged()
    }
}
