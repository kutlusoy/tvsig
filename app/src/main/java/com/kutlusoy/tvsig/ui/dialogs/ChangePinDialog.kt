package com.kutlusoy.tvsig.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.databinding.DialogChangePinBinding
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper

/** Three steps with one number pad: current PIN, new PIN, confirmation. */
class ChangePinDialog : DialogFragment() {

    private enum class Step { CURRENT, NEW, CONFIRM }

    private var _binding: DialogChangePinBinding? = null
    private val binding get() = _binding!!

    private var verifyCurrent: ((String) -> Boolean)? = null
    private var onPinChanged: ((String) -> Unit)? = null
    private var step = Step.CURRENT
    private var newPin = ""

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
        binding.pinPad.onPinComplete = ::onPinEntered
        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        showStep(Step.CURRENT)
        binding.pinPad.focusFirstKey()
    }

    private fun onPinEntered(pin: String) {
        when (step) {
            Step.CURRENT -> {
                if (verifyCurrent?.invoke(pin) == true) {
                    showStep(Step.NEW)
                } else {
                    showStep(Step.CURRENT, getString(R.string.pin_incorrect))
                }
            }
            Step.NEW -> {
                newPin = pin
                showStep(Step.CONFIRM)
            }
            Step.CONFIRM -> {
                if (pin == newPin) {
                    onPinChanged?.invoke(pin)
                    dismissAllowingStateLoss()
                } else {
                    showStep(Step.NEW, getString(R.string.error_pins_dont_match))
                }
            }
        }
    }

    private fun showStep(next: Step, error: String = "") {
        step = next
        binding.pinStepTitle.setText(
            when (next) {
                Step.CURRENT -> R.string.pin_current
                Step.NEW -> R.string.pin_new
                Step.CONFIRM -> R.string.pin_confirm_new
            }
        )
        binding.pinStatus.text = error
        binding.pinPad.reset()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "ChangePinDialog"

        fun show(
            fragmentManager: FragmentManager,
            verifyCurrent: (String) -> Boolean,
            onPinChanged: (String) -> Unit
        ) {
            try {
                ChangePinDialog().apply {
                    this.verifyCurrent = verifyCurrent
                    this.onPinChanged = onPinChanged
                }.show(fragmentManager, TAG)
            } catch (e: Exception) {
                Logger.e("Error showing change PIN dialog", e)
            }
        }
    }
}
