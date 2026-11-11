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
import androidx.fragment.app.activityViewModels
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.databinding.DialogRemoteSetupBinding
import com.kutlusoy.tvsig.remote.RemoteSetupServer
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.NetworkHelper
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper
import com.kutlusoy.tvsig.utils.QrCode
import com.kutlusoy.tvsig.viewmodels.SettingsViewModel
import java.security.SecureRandom

/** Shows a QR code that opens the setup page on the phone, the server runs only while this dialog is open. */
class RemoteSetupDialog : DialogFragment() {

    private var _binding: DialogRemoteSetupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by activityViewModels()

    private var server: RemoteSetupServer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { dismissAllowingStateLoss() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRemoteSetupBinding.inflate(inflater, container, false)
        val orientation = PreferenceHelper.getInstance(inflater.context).getOrientation()
        return OrientationHelper.wrapDialogView(inflater.context, binding.root, orientation, maxWidthDp = 520)
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
        binding.btnClose.setOnClickListener { dismissAllowingStateLoss() }
        startServer()
    }

    private fun startServer() {
        val context = requireContext().applicationContext
        val address = NetworkHelper.localIpv4(context)
        if (address == null) {
            binding.qrImage.visibility = View.GONE
            binding.remoteStatus.setText(R.string.remote_no_network)
            return
        }

        val token = newToken()
        try {
            val newServer = RemoteSetupServer(
                context = context,
                token = token,
                currentSettings = { viewModel.settings.value },
                onSave = { updated -> mainHandler.post { viewModel.applyRemote(updated) } }
            )
            val port = newServer.start()
            server = newServer

            val url = "http://$address:$port/?t=$token"
            binding.qrImage.setImageBitmap(QrCode.create(url, QR_SIZE))
            binding.remoteUrl.text = "$address:$port"
            mainHandler.postDelayed(timeout, TIMEOUT_MS)
        } catch (e: Exception) {
            Logger.e("Could not start the setup server", e)
            binding.qrImage.visibility = View.GONE
            binding.remoteStatus.setText(R.string.remote_failed)
        }
    }

    private fun newToken(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onDestroyView() {
        mainHandler.removeCallbacks(timeout)
        server?.stop()
        server = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "RemoteSetupDialog"
        private const val QR_SIZE = 520
        private const val TIMEOUT_MS = 10 * 60 * 1000L

        fun show(fragmentManager: FragmentManager) {
            try {
                RemoteSetupDialog().show(fragmentManager, TAG)
            } catch (e: Exception) {
                Logger.e("Error showing the setup dialog", e)
            }
        }
    }
}
