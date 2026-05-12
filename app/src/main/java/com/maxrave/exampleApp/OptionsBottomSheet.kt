package com.maxrave.exampleApp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class OptionsBottomSheet(
    private val track: Any,
    private val onActionSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Crie um layout simples chamado layout_options_bottom_sheet.xml com estes IDs
        val view = inflater.inflate(R.layout.layout_options_bottom_sheet, container, false)

        view.findViewById<TextView>(R.id.btnPlayNext).setOnClickListener {
            onActionSelected("PLAY_NEXT")
            dismiss()
        }
        
        view.findViewById<TextView>(R.id.btnAddToQueue).setOnClickListener {
            onActionSelected("ADD_QUEUE")
            dismiss()
        }

        view.findViewById<TextView>(R.id.btnDownloadMp3).setOnClickListener {
            onActionSelected("DOWNLOAD_MP3")
            dismiss()
        }

        view.findViewById<TextView>(R.id.btnAddToPlaylist).setOnClickListener {
            onActionSelected("ADD_PLAYLIST")
            dismiss()
        }

        return view
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Define um estilo para o BottomSheet ter fundo transparente e permitir bordas arredondadas no XML
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
}
}
