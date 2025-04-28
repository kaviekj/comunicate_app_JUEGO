package com.example.comunicate_apptest

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.gridlayout.widget.GridLayout
import kotlin.math.ceil

class SecondActivity : AppCompatActivity() {

    private lateinit var gridLayout: GridLayout
    private var firstCard: ImageButton? = null
    private var firstTag: String? = null
    private var isProcessing = false
    private var matchedPairs = 0

    // Tus 27 letras
    private val letters = listOf(
        "a","b","c","d","e","f","g","h","i","j",
        "k","l","m","n","enne","o","p","q","r","s",
        "t","u","v","w","x","y","z"
    )
    private val cardValues = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)
        gridLayout = findViewById(R.id.gridLayout)

        setupGame()
    }

    private fun setupGame() {
        // 1) Prepara y baraja
        cardValues.clear()
        letters.forEach { letter ->
            cardValues += letter
            cardValues += "sign_$letter"
        }
        cardValues.shuffle()

        // 2) Columnas y filas
        val cols = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 8 else 6
        val rows = ceil(cardValues.size.toDouble() / cols).toInt()

        gridLayout.columnCount = cols
        gridLayout.rowCount    = rows
        gridLayout.removeAllViews()

        // 3) Margen en px
        val margin = (4 * resources.displayMetrics.density).toInt()

        // 4) Añade cada carta con peso 1 en fila y columna (usa 0px + weight)
        cardValues.forEach { tag ->
            val card = ImageButton(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width  = 0
                    height = 0
                    rowSpec    = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(margin, margin, margin, margin)
                }
                setScaleType(ImageView.ScaleType.CENTER_CROP)
                setImageResource(R.drawable.card_back)
                this.tag = tag
                setOnClickListener { onCardClick(this) }
                background = null
            }
            gridLayout.addView(card)
        }
    }

    private fun onCardClick(card: ImageButton) {
        if (isProcessing || card == firstCard) return

        // 1) Anima volteo
        isProcessing = true
        ObjectAnimator.ofFloat(card, "rotationY", 0f, 90f).apply {
            duration = 200
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                    // 2) Cambia imagen en la mitad
                    val resId = resources.getIdentifier(card.tag as String, "drawable", packageName)
                    card.setImageResource(resId)

                    // Segunda mitad de la animación
                    ObjectAnimator.ofFloat(card, "rotationY", -90f, 0f).apply {
                        duration = 200
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animator: Animator) {
                                onFlipFinished(card)
                            }
                        })
                        start()
                    }
                }
            })
            start()
        }
    }


    private fun onFlipFinished(card: ImageButton) {
        if (isDestroyed) return

        try {
            if (firstCard == null) {
                firstCard = card
                firstTag = card.tag?.toString() ?: return
                isProcessing = false
            } else {
                val secondTag = card.tag?.toString() ?: return
                if (firstTag?.removePrefix("sign_") == secondTag.removePrefix("sign_")) {
                    // Coincidencia
                    card.isEnabled = false
                    firstCard?.isEnabled = false
                    matchedPairs++
                    resetSelection()
                } else {
                    // No coinciden - usar corrutina segura
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isDestroyed && ::gridLayout.isInitialized) {
                            firstCard?.let { flip(it, showFront = false) }
                            flip(card, showFront = false)
                            resetSelection()
                        }
                    }, 1000)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            resetSelection()
        }
    }


    private fun flip(card: ImageButton, showFront: Boolean, onEnd: () -> Unit = {}) {
        card.isEnabled = false

        // Verificar si la tarjeta sigue vinculada a la actividad
        if (isDestroyed || isFinishing) return

        // Primera mitad del giro
        ObjectAnimator.ofFloat(card, "rotationY", 0f, 90f).apply {
            duration = 250
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    try {
                        // Cambiar imagen de manera segura
                        if (showFront) {
                            val tag = card.tag as? String ?: return
                            val resId = resources.getIdentifier(tag, "drawable", packageName)
                            if (resId != 0) {
                                card.setImageResource(resId)
                            }
                        } else {
                            card.setImageResource(R.drawable.card_back)
                        }
                        card.alpha = if (showFront) 1f else 1f

                        // Segunda mitad del giro
                        ObjectAnimator.ofFloat(card, "rotationY", -90f, 0f).apply {
                            duration = 250
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    if (!isDestroyed) {
                                        card.isEnabled = true
                                        onEnd()
                                    }
                                }
                            })
                            start()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            })
            start()
        }
    }




    private fun resetSelection() {
        firstCard = null
        firstTag  = null
        isProcessing = false
    }
}
