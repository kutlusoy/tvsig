package com.kutlusoy.tvsig.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.kutlusoy.tvsig.databinding.DialogPinBinding
import com.kutlusoy.tvsig.models.Constants
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper
import com.kutlusoy.tvsig.ui.activities.MainActivity

class PinDialog : DialogFragment() {

    private var _binding: DialogPinBinding? = null
    private val binding get() = _binding!!

    private var onPinEntered: ((String) -> Unit)? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())

    private val timeoutRunnable = Runnable {
        dismiss()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPinBinding.inflate(inflater, container, false)
        val orientation = PreferenceHelper.getInstance(inflater.context).getOrientation()
        return OrientationHelper.wrapDialogView(inflater.context, binding.root, orientation)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPinInputs()
        setupButtons()
        startTimeoutTimer()
    }

    private fun setupPinInputs() {
        val inputs = listOf(
            binding.pinDigit1,
            binding.pinDigit2,
            binding.pinDigit3,
            binding.pinDigit4
        )

        inputs.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1) {
                        if (index < inputs.size - 1) {
                            inputs[index + 1].requestFocus()
                        } else {
                            val pin = inputs.joinToString("") { it.text.toString() }
                            if (pin.length == Constants.PIN_LENGTH) {
                                onPinEntered?.invoke(pin)
                                dismiss()
                            }
                        }
                    }
                }
            })
        }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnExit.setOnClickListener {
            (activity as? MainActivity)?.exit()
        }
    }

    private fun startTimeoutTimer() {
        timeoutHandler.postDelayed(timeoutRunnable, Constants.PIN_DIALOG_TIMEOUT_MS)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timeoutHandler.removeCallbacks(timeoutRunnable)
        _binding = null
    }

    companion object {
        private const val TAG = "PinDialog"

        fun show(fragmentManager: FragmentManager, onPinEntered: (String) -> Unit) {
            try {
                val dialog = PinDialog().apply {
                    this.onPinEntered = onPinEntered
                }
                dialog.show(fragmentManager, TAG)
            } catch (e: Exception) {
                Logger.e("Error showing PIN dialog", e)
            }
        }
    }
}