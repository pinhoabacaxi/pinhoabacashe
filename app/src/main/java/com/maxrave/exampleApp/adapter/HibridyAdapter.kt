package com.maxrave.exampleApp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.maxrave.exampleApp.R
import com.maxrave.exampleApp.databinding.ItemMusicBinding
import com.maxrave.exampleApp.model.Song
import com.maxrave.exampleApp.model.OnlineSong
import com.maxrave.kotlinyoutubeextractor.VideoMeta

class HybridAdapter(
    private val onItemClick: (Any) -> Unit,
    private val onMoreOptionsClick: (Any) -> Unit
) : RecyclerView.Adapter<HybridAdapter.MusicViewHolder>() {

    private var items = mutableListOf<Any>()

    fun setList(newList: List<Any>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Song -> TYPE_LOCAL
            is OnlineSong, is VideoMeta -> TYPE_ONLINE
            else -> TYPE_LOCAL
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val binding = ItemMusicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MusicViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class MusicViewHolder(private val binding: ItemMusicBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Any) {
            when (item) {
                is Song -> {
                    binding.txtTitle.text = item.title
                    binding.txtArtist.text = item.artist
                    // Carrega capa local (MediaStore)
                    val uri = android.content.ContentUris.withAppendedId(
                        android.util.Size(50, 50) // apenas exemplo
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.id
                    )
                    Glide.with(binding.imgArt).load(uri).placeholder(R.drawable.ic_default_art).into(binding.imgArt)
                    binding.imgSourceType.setImageResource(R.drawable.ic_folder) // Ícone de pasta
                }
                is VideoMeta -> { // Usado na busca
                    binding.txtTitle.text = item.title
                    binding.txtArtist.text = item.author
                    Glide.with(binding.imgArt).load(item.thumbnailUrl).into(binding.imgArt)
                    binding.imgSourceType.setImageResource(R.drawable.ic_cloud) // Ícone de nuvem
                }
                is OnlineSong -> { // Usado na fila/playlist
                    binding.txtTitle.text = item.title
                    binding.txtArtist.text = item.author
                    Glide.with(binding.imgArt).load(item.thumbnailUrl).into(binding.imgArt)
                    binding.imgSourceType.setImageResource(R.drawable.ic_cloud)
                }
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnMore.setOnClickListener { onMoreOptionsClick(item) }
        }
    }

    companion object {
        private const val TYPE_LOCAL = 0
        private const val TYPE_ONLINE = 1
    }
}
