package com.maxrave.exampleApp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.maxrave.exampleApp.model.OnlineSong

class OptionsBottomSheet(
    private val track: Any,
    private val onActionSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Define o estilo para permitir bordas arredondadas e fundo transparente
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, 
        container: ViewGroup?, 
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_options_bottom_sheet, container, false)

        // 1. Tocar a seguir
        view.findViewById<TextView>(R.id.btnPlayNext).setOnClickListener {
            onActionSelected("PLAY_NEXT")
            dismiss()
        }
        
        // 2. Adicionar ao final da fila
        view.findViewById<TextView>(R.id.btnAddToQueue).setOnClickListener {
            onActionSelected("ADD_QUEUE")
            dismiss()
        }

        // 3. Baixar MP3 (Apenas se for música Online)
        view.findViewById<TextView>(R.id.btnDownloadMp3).apply {
            if (track !is OnlineSong) {
                // Se a música já for local, podemos esconder ou desativar a opção
                this.alpha = 0.5f
                this.setOnClickListener {
                    Toast.makeText(context, "Esta música já está no seu dispositivo", Toast.LENGTH_SHORT).show()
                }
            } else {
                this.setOnClickListener {
                    onActionSelected("DOWNLOAD_MP3")
                    dismiss()
                }
            }
        }

        // 4. Adicionar à Playlist (Permanente)
        view.findViewById<TextView>(R.id.btnAddToPlaylist).setOnClickListener {
            onActionSelected("ADD_PLAYLIST")
            dismiss()
        }

        return view
    }
}
