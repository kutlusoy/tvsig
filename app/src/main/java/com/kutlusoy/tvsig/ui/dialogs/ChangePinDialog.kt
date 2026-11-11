package com.kutlusoy.tvsig.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.snackbar.Snackbar
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.databinding.DialogChangePinBinding
import com.kutlusoy.tvsig.models.Constants
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper

class ChangePinDialog : DialogFragment() {

    private var _binding: DialogChangePinBinding? = null
    private val binding get() = _binding!!

    private var onPinChanged: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogChangePinBinding.inflate(inflater, container, false)
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
    }

    private fun setupPinInputs() {
        setupPinDigitGroup(
            listOf(
                binding.currentPinDigit1,
                binding.currentPinDigit2,
                binding.currentPinDigit3,
                binding.currentPinDigit4
            )
        )

        setupPinDigitGroup(
            listOf(
                binding.newPinDigit1,
                binding.newPinDigit2,
                binding.newPinDigit3,
                binding.newPinDigit4
            )
        )

        setupPinDigitGroup(
            listOf(
                binding.confirmPinDigit1,
                binding.confirmPinDigit2,
                binding.confirmPinDigit3,
                binding.confirmPinDigit4
            )
        )
    }

    private fun setupPinDigitGroup(inputs: List<EditText>) {
        inputs.forEachIndexed { index, editText ->
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < inputs.size - 1) {
                        inputs[index + 1].requestFocus()
                    }
                }
            })
        }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnSave.setOnClickListener {
            validateAndSavePin()
        }
    }

    private fun validateAndSavePin() {
        val currentPin = getPinFromInputs(
            binding.currentPinDigit1,
            binding.currentPinDigit2,
            binding.currentPinDigit3,
            binding.currentPinDigit4
        )

        val newPin = getPinFromInputs(
            binding.newPinDigit1,
            binding.newPinDigit2,
            binding.newPinDigit3,
            binding.newPinDigit4
        )

        val confirmPin = getPinFromInputs(
            binding.confirmPinDigit1,
            binding.confirmPinDigit2,
            binding.confirmPinDigit3,
            binding.confirmPinDigit4
        )

        when {
            currentPin.length != Constants.PIN_LENGTH -> {
                showError(getString(R.string.pin_incorrect))
            }
            newPin.length != Constants.PIN_LENGTH -> {
                showError(getString(R.string.error_invalid_pin))
            }
            newPin != confirmPin -> {
                showError(getString(R.string.error_pins_dont_match))
            }
            else -> {
                onPinChanged?.invoke(newPin)
                dismiss()
            }
        }
    }

    private fun getPinFromInputs(vararg inputs: EditText): String {
        return inputs.joinToString("") { it.text.toString() }
    }

    private fun showError(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ChangePinDialog"

        fun show(fragmentManager: FragmentManager, onPinChanged: (String) -> Unit) {
            try {
                val dialog = ChangePinDialog().apply {
                    this.onPinChanged = onPinChanged
                }
                dialog.show(fragmentManager, TAG)
            } catch (e: Exception) {
                Logger.e("Error showing Change PIN dialog", e)
            }
        }
    }
}