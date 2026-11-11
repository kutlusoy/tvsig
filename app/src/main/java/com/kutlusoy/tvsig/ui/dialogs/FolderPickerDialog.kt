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
import com.kutlusoy.tvsig.models.FolderPath
import com.kutlusoy.tvsig.models.Result
import com.kutlusoy.tvsig.models.Source
import com.kutlusoy.tvsig.repositories.MediaRepository
import com.kutlusoy.tvsig.utils.Logger
import com.kutlusoy.tvsig.utils.OrientationHelper
import com.kutlusoy.tvsig.utils.PreferenceHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Browses the folders of the source level by level, up to five levels deep, and returns the chosen path. */
class FolderPickerDialog : DialogFragment() {

    private sealed class Item {
        object Use : Item()
        object Up : Item()
        data class Folder(val name: String) : Item()
    }

    private var _binding: DialogFolderPickerBinding? = null
    private val binding get() = _binding!!

    private var source: Source? = null
    private var path = ""
    private var onSelected: ((String) -> Unit)? = null
    private var loadJob: Job? = null

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
        binding.folderList.setOnItemClickListener { _, _, position, _ ->
            when (val item = binding.folderList.adapter.getItem(position) as Item) {
                Item.Use -> {
                    onSelected?.invoke(path)
                    dismissAllowingStateLoss()
                }
                Item.Up -> {
                    val previous = FolderPath.name(path)
                    path = FolderPath.parent(path)
                    load(selectName = previous)
                }
                is Item.Folder -> {
                    path = FolderPath.child(path, item.name)
                    load()
                }
            }
        }
        load()
    }

    private fun load(selectName: String? = null) {
        val currentSource = source ?: return
        binding.folderPath.text = FolderPath.display(path, getString(R.string.folder_picker_root))
        binding.folderStatus.setText(R.string.folder_picker_loading)
        binding.folderList.visibility = View.INVISIBLE

        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val atMaxDepth = FolderPath.depth(path) >= FolderPath.MAX_DEPTH
            val folders = if (atMaxDepth) {
                Result.Success(emptyList())
            } else {
                MediaRepository.getInstance(requireContext()).listSubfolders(currentSource, path)
            }
            when (folders) {
                is Result.Success -> {
                    binding.folderStatus.text = when {
                        atMaxDepth -> getString(R.string.folder_picker_max_depth, FolderPath.MAX_DEPTH)
                        folders.data.isEmpty() -> getString(R.string.folder_picker_empty)
                        else -> ""
                    }
                    showItems(folders.data, selectName)
                }
                is Result.Error -> {
                    binding.folderStatus.text = getString(
                        R.string.folder_picker_error,
                        folders.exception.message ?: folders.exception.javaClass.simpleName
                    )
                    showItems(emptyList(), selectName)
                }
                else -> {}
            }
        }
    }

    private fun showItems(folders: List<String>, selectName: String?) {
        val items = mutableListOf<Item>(Item.Use)
        if (FolderPath.depth(path) > 0) items.add(Item.Up)
        folders.forEach { items.add(Item.Folder(it)) }

        binding.folderList.apply {
            adapter = object : ArrayAdapter<Item>(requireContext(), R.layout.item_folder, items) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as android.widget.TextView
                    view.text = when (val item = getItem(position)) {
                        Item.Use -> getString(R.string.folder_picker_use)
                        Item.Up -> getString(R.string.folder_picker_up)
                        is Item.Folder -> getString(R.string.folder_picker_folder, item.name)
                        null -> ""
                    }
                    return view
                }
            }
            visibility = View.VISIBLE
            setSelection(items.indexOfFirst { it is Item.Folder && it.name == selectName }.takeIf { it >= 0 } ?: 0)
            requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "FolderPickerDialog"

        fun show(fragmentManager: FragmentManager, source: Source, onSelected: (String) -> Unit) {
            try {
                FolderPickerDialog().apply {
                    this.source = source
                    this.path = ""
                    this.onSelected = onSelected
                }.show(fragmentManager, TAG)
            } catch (e: Exception) {
                Logger.e("Error showing the folder picker", e)
            }
        }
    }
}
