package com.appsonair.applink.utils

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Utils {
    companion object {
        @JvmStatic
        fun showToastMsg(context: Context, message: String = "") {
            if (message.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}