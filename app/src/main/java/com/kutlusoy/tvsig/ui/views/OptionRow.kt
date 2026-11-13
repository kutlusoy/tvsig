package com.kutlusoy.tvsig.ui.views

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.databinding.ViewOptionRowBinding

/**
 * A settings row for remote controls: left and right change the value, OK steps forward.
 * The title stands above the value, so the row looks the same on every width and rotation.
 */
class OptionRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding = ViewOptionRowBinding.inflate(LayoutInflater.from(context), this)
    private var options: List<String> = emptyList()

    var selectedIndex = 0
        private set

    var onSelectionChanged: ((Int) -> Unit)? = null

    init {
        orientation = VERTICAL
        minimumHeight = resources.getDimensionPixelSize(R.dimen.row_min_height)
        val padding = resources.getDimensionPixelSize(R.dimen.margin_medium)
        setPadding(padding, padding / 2, padding, padding / 2)
        setBackgroundResource(R.drawable.bg_row)
        isFocusable = true
        isClickable = true

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.OptionRow)
        try {
            binding.optionTitle.text = typedArray.getString(R.styleable.OptionRow_optionTitle)
        } finally {
            typedArray.recycle()
        }
        setOnClickListener { step(1) }
        binding.optionPrevious.setOnClickListener { step(-1) }
        binding.optionNext.setOnClickListener { step(1) }
    }

    fun setOptions(options: List<String>, selected: Int) {
        this.options = options
        select(selected, notify = false)
    }

    fun select(index: Int, notify: Boolean) {
        if (options.isEmpty()) return
        selectedIndex = index.coerceIn(0, options.lastIndex)
        binding.optionValue.text = options[selectedIndex]
        contentDescription = "${binding.optionTitle.text}: ${options[selectedIndex]}"
        if (notify) onSelectionChanged?.invoke(selectedIndex)
    }

    private fun step(delta: Int) {
        if (options.isEmpty()) return
        select((selectedIndex + delta + options.size) % options.size, notify = true)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { step(-1); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { step(1); true }
        else -> super.onKeyDown(keyCode, event)
    }
}
