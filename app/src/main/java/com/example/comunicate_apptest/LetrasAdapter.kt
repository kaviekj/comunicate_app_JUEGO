package com.example.comunicate_apptest

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class LetrasAdapter(
    private val context: Context,
    private val letras: List<String>
) : BaseAdapter() {

    override fun getCount(): Int = letras.size
    override fun getItem(position: Int): Any = letras[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.grid_item_letra, parent, false)

        val letra = letras[position]
        val imagenLetra = view.findViewById<ImageView>(R.id.imgLetra)
        val textoLetra = view.findViewById<TextView>(R.id.txtLetra)

        val nombreImagen = letra.toLowerCase().replace("ñ", "enne")
        val resourceId = context.resources.getIdentifier(
            nombreImagen,
            "drawable",
            context.packageName
        )

        imagenLetra.setImageResource(
            if (resourceId != 0) resourceId
            else android.R.color.transparent
        )

        textoLetra.text = letra
        return view
    }
}