// Adapted from the Android "Common Logger" sample.
// Copyright (C) The Android Open Source Project
// Licensed under the Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
package org.androidlabs.filewall.logger

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.androidlabs.filewall.R

class ConfigurationChangeObserver(
    private val context: Context,
    private val callback: (Boolean) -> Unit
) :
    DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        detectThemeChange()
    }

    private fun detectThemeChange() {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = uiMode == Configuration.UI_MODE_NIGHT_YES
        callback(isDarkMode)
    }
}

/**
 * Simple fragment which contains a LogView and uses is to output log data it receives
 * through the LogNode interface.
 */
class LogFragment : Fragment() {

    var logNode: MessageOnlyLogFilter? = null

    private var configurationObserver: ConfigurationChangeObserver? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LogAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)

        configurationObserver = ConfigurationChangeObserver(context) { isDarkMode ->
            val theme = requireActivity().theme
            val resolvedAttr = TypedValue()
            theme?.resolveAttribute(
                if (isDarkMode) android.R.attr.textColorPrimaryInverse else android.R.attr.textColorPrimary,
                resolvedAttr,
                true
            )

            val textColor = requireActivity().getColor(resolvedAttr.resourceId)
            theme?.resolveAttribute(
                if (isDarkMode) android.R.attr.textColorPrimary else android.R.attr.textColorPrimaryInverse,
                resolvedAttr,
                true
            )
            val bgColor = requireActivity().getColor(resolvedAttr.resourceId)

            adapter.textColor = textColor
            recyclerView.setBackgroundColor(bgColor)
            adapter.notifyDataSetChanged()
        }

        lifecycle.addObserver(configurationObserver!!)
    }

    override fun onDetach() {
        super.onDetach()

        configurationObserver?.let {
            lifecycle.removeObserver(it)
        }
        configurationObserver = null
    }

    private var progressDialog: AlertDialog? = null
    private fun showLoadingDialog() {
        progressDialog = AlertDialog.Builder(requireContext(),R.style.Theme_Filewall_Dialog)
            .setView(ProgressBar(requireContext()))
            .setCancelable(false)
            .create()
        progressDialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        progressDialog?.show()
    }

    private fun hideLoadingDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
        }
        adapter = LogAdapter(mutableListOf())
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            showLoadingDialog()

            val logs = withContext(Dispatchers.IO) {
                val fileLogger = FileLogger(requireContext())
                fileLogger.readLogs()
            }

            adapter.setLogs(logs)
            recyclerView.scrollToPosition(adapter.itemCount - 1)

            hideLoadingDialog()
        }

        return recyclerView
    }

    override fun onResume() {
        super.onResume()
    }
}