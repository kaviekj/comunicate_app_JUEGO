package com.example.comunicate_apptest

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.gridlayout.widget.GridLayout

class SecondActivity : AppCompatActivity() {

    private lateinit var gridLayout: GridLayout
    private var firstCard: ImageButton? = null
    private var firstCardTag: String? = null
    private var isProcessing = false
    private var matchedPairs = 0

    private val letters = listOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j",
        "k", "l", "m", "n", "enne", "o", "p", "q", "r", "s",
        "t", "u", "v", "w", "x", "y", "z"
    )

    private val cardValues = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        gridLayout = findViewById(R.id.gridLayout)
        setupGame()
    }

    private fun setupGame() {
        // Crear pares de cartas
        letters.forEach { letter ->
            cardValues.add(letter)
            cardValues.add("sign_$letter")
        }
        cardValues.shuffle()

        // Configurar tamaño de las cartas
        val margin = 8
        val screenWidth = resources.displayMetrics.widthPixels
        val cardSize = (screenWidth - (6 + 1) * margin) / 6

        // Agregar cartas al GridLayout
        cardValues.forEach { tag ->
            val card = ImageButton(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cardSize
                    height = cardSize
                    setMargins(margin, margin, margin, margin)
                }
                setBackgroundResource(R.drawable.card_back)
                scaleType = ImageView.ScaleType.CENTER_CROP
                this.tag = tag
                setOnClickListener { handleCardClick(it as ImageButton) }
            }
            gridLayout.addView(card)
        }
    }

    private fun handleCardClick(card: ImageButton) {
        if (isProcessing || card == firstCard || card.tag == null) return

        flipCard(card) {
            if (firstCard == null) {
                firstCard = card
                firstCardTag = card.tag as String
            } else {
                checkForMatch(card)
            }
        }
    }

    private fun checkForMatch(secondCard: ImageButton) {
        isProcessing = true
        val secondTag = secondCard.tag as String

        if (isMatchingPair(firstCardTag!!, secondTag)) {
            disableCards(firstCard!!, secondCard)
            matchedPairs++
            checkGameOver()
            resetSelection()
        } else {
            flipBackCards(firstCard!!, secondCard)
        }
    }

    private fun flipCard(card: ImageButton, callback: () -> Unit) {
        card.isEnabled = false
        ObjectAnimator.ofFloat(card, "rotationY", 0f, 180f).apply {
            duration = 500
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    callback()
                    card.isEnabled = true
                }
            })
            start()
        }

        // Cambiar imagen a la mitad de la animación
        Handler(Looper.getMainLooper()).postDelayed({
            val resId = resources.getIdentifier(card.tag as String, "drawable", packageName)
            card.setImageResource(resId)
        }, 250)
    }

    private fun flipBackCards(vararg cards: ImageButton) {
        cards.forEach { card ->
            card.isEnabled = false
            ObjectAnimator.ofFloat(card, "rotationY", 180f, 0f).apply {
                duration = 500
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        card.setImageResource(R.drawable.card_back)
                        card.isEnabled = true
                    }
                })
                start()
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            resetSelection()
        }, 1000)
    }

    private fun disableCards(vararg cards: ImageButton) {
        cards.forEach {
            it.isEnabled = false
            it.alpha = 0.5f
        }
    }

    private fun isMatchingPair(tag1: String, tag2: String): Boolean {
        val base1 = tag1.removePrefix("sign_")
        val base2 = tag2.removePrefix("sign_")
        return base1 == base2
    }

    private fun resetSelection() {
        firstCard = null
        firstCardTag = null
        isProcessing = false
    }

    private fun checkGameOver() {
        if (matchedPairs == letters.size) {
            // Todo: Mostrar mensaje de victoria
        }
    }
}