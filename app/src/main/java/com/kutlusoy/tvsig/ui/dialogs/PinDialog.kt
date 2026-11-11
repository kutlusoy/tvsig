package com.kutlusoy.tvsig.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.kutlusoy.tvsig.databinding.DialogPinBinding
import com.kutlusoy.tvsig.models.Constants
import com.kutlusoy.tvsig.ui.activities.MainActivity
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper

class PinDialog : DialogFragment() {

    private var _binding: DialogPinBinding? = null
    private val binding get() = _binding!!

    private var onPinEntered: ((String) -> Unit)? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable { dismissAllowingStateLoss() }

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

        binding.pinPad.onPinComplete = { pin ->
            onPinEntered?.invoke(pin)
            dismissAllowingStateLoss()
        }
        binding.pinPad.focusFirstKey()
        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnExit.setOnClickListener { (activity as? MainActivity)?.exit() }
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
                PinDialog().apply { this.onPinEntered = onPinEntered }.show(fragmentManager, TAG)
            } catch (e: Exception) {
                Logger.e("Error showing PIN dialog", e)
            }
        }
    }
}
