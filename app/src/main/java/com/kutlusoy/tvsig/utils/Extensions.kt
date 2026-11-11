package com.kutlusoy.tvsig.utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.showSnackbar(
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT,
    action: String? = null,
    actionClick: (() -> Unit)? = null
) {
    Snackbar.make(this, message, duration).apply {
        action?.let { actionText ->
            setAction(actionText) { actionClick?.invoke() }
        }
    }.show()
}

fun View.showSnackbar(
    @StringRes messageRes: Int,
    duration: Int = Snackbar.LENGTH_SHORT,
    action: String? = null,
    actionClick: (() -> Unit)? = null
) {
    showSnackbar(context.getString(messageRes), duration, action, actionClick)
}

fun Activity.hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    val currentFocus = currentFocus
    if (currentFocus != null) {
        imm?.hideSoftInputFromWindow(currentFocus.windowToken, 0)
    }
}

fun Activity.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Activity.showToast(@StringRes messageRes: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, messageRes, duration).show()
}

fun Fragment.hideKeyboard() {
    activity?.hideKeyboard()
}

fun Fragment.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    activity?.showToast(message, duration)
}

fun Fragment.showToast(@StringRes messageRes: Int, duration: Int = Toast.LENGTH_SHORT) {
    activity?.showToast(messageRes, duration)
}

fun EditText.onTextChanged(listener: (String) -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) {}
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            listener(s?.toString() ?: "")
        }
    })
}

fun CoroutineScope.debounce(
    delayMillis: Long,
    action: suspend () -> Unit
): Job {
    var debounceJob: Job? = null
    return launch {
        debounceJob?.cancel()
        debounceJob = launch {
            delay(delayMillis)
            action()
        }
    }
}