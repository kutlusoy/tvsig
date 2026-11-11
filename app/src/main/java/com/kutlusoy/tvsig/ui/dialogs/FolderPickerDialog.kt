package com.kutlusoy.tvsig.ui.dialogs

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.kutlusoy.tvsig.R
import com.kutlusoy.tvsig.databinding.DialogFolderPickerBinding
import com.kutlusoy.tvsig.models.Result
import com.kutlusoy.tvsig.repositories.MediaRepository
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper
import kotlinx.coroutines.launch

/** Lets the user pick a subfolder of the base URL from a list, no typing needed. */
class FolderPickerDialog : DialogFragment() {

    private var _binding: DialogFolderPickerBinding? = null
    private val binding get() = _binding!!

    private var baseUrl = ""
    private var current = ""
    private var onSelected: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFolderPickerBinding.inflate(inflater, container, false)
        val orientation = PreferenceHelper.getInstance(inflater.context).getOrientation()
        return OrientationHelper.wrapDialogView(inflater.context, binding.root, orientation, maxWidthDp = 680)
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
        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.folderStatus.setText(R.string.folder_picker_loading)

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = MediaRepository.getInstance(requireContext()).listSubfolders(baseUrl)) {
                is Result.Success -> showFolders(result.data)
                is Result.Error -> {
                    binding.folderStatus.text = getString(
                        R.string.folder_picker_error,
                        result.exception.message ?: result.exception.javaClass.simpleName
                    )
                    binding.btnCancel.requestFocus()
                }
                else -> {}
            }
        }
    }

    private fun showFolders(folders: List<String>) {
        binding.folderStatus.text = if (folders.isEmpty()) getString(R.string.folder_picker_empty) else ""
        val entries = listOf(getString(R.string.folder_picker_root)) + folders
        binding.folderList.apply {
            adapter = ArrayAdapter(requireContext(), R.layout.item_folder, entries)
            visibility = View.VISIBLE
            setOnItemClickListener { _, _, position, _ ->
                onSelected?.invoke(if (position == 0) "" else folders[position - 1])
                dismissAllowingStateLoss()
            }
            setSelection(folders.indexOf(current) + 1)
            requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "FolderPickerDialog"

        fun show(fragmentManager: FragmentManager, baseUrl: String, current: String, onSelected: (String) -> Unit) {
            try {
                FolderPickerDialog().apply {
                    this.baseUrl = baseUrl
                    this.current = current
                    this.onSelected = onSelected
                }.show(fragmentManager, TAG)
            } catch (e: Exception) {
                Logger.e("Error showing the folder picker", e)
            }
        }
    }
}
