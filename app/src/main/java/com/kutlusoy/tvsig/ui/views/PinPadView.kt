package com.kutlusoy.tvsig.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.models.Constants

/** On-screen number pad that works with arrow keys and OK, number keys of the remote work as well. */
class PinPadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val dots = TextView(context)
    private val pin = StringBuilder()

    var onPinComplete: ((String) -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        dots.apply {
            textSize = 40f
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface))
        }
        addView(dots, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = resources.getDimensionPixelSize(R.dimen.margin_medium)
        })

        val grid = GridLayout(context).apply { columnCount = 3 }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", KEY_BACK, "0", KEY_CLEAR)
        keys.forEach { label -> grid.addView(createKey(label)) }
        addView(grid)
        updateDots()
    }

    fun reset() {
        pin.clear()
        updateDots()
    }

    fun focusFirstKey() {
        (getChildAt(1) as GridLayout).getChildAt(0).requestFocus()
    }

    private fun createKey(label: String): Button {
        val key = LayoutInflater.from(context).inflate(R.layout.view_pin_key, this, false) as Button
        key.text = label
        key.layoutParams = GridLayout.LayoutParams().apply {
            width = resources.getDimensionPixelSize(R.dimen.pin_key_size)
            height = resources.getDimensionPixelSize(R.dimen.pin_key_size)
            setMargins(6, 6, 6, 6)
        }
        key.setOnClickListener {
            when (label) {
                KEY_BACK -> backspace()
                KEY_CLEAR -> reset()
                else -> append(label[0])
            }
        }
        return key
    }

    private fun append(digit: Char) {
        if (pin.length >= Constants.PIN_LENGTH) return
        pin.append(digit)
        updateDots()
        if (pin.length == Constants.PIN_LENGTH) onPinComplete?.invoke(pin.toString())
    }

    private fun backspace() {
        if (pin.isNotEmpty()) pin.deleteCharAt(pin.length - 1)
        updateDots()
    }

    private fun updateDots() {
        dots.text = (0 until Constants.PIN_LENGTH).joinToString(" ") { if (it < pin.length) "●" else "○" }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val digit = digitOf(event.keyCode)
            if (digit != null) {
                append(digit)
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_DEL) {
                backspace()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun resolveColor(attr: Int): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(attr, value, true)
        return value.data
    }

    companion object {
        private const val KEY_BACK = "⌫"
        private const val KEY_CLEAR = "C"

        fun digitOf(keyCode: Int): Char? = when (keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> '0' + (keyCode - KeyEvent.KEYCODE_0)
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> '0' + (keyCode - KeyEvent.KEYCODE_NUMPAD_0)
            else -> null
        }
    }
}
