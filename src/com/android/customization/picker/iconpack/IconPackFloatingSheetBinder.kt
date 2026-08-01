/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.customization.picker.iconpack

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption
import com.android.wallpaper.picker.customization.ui.util.CustomizationOptionUtil.CustomizationOption
import com.android.themepicker.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object IconPackFloatingSheetBinder {

    fun bind(
        view: View,
        lifecycleOwner: LifecycleOwner,
        selectedOption: Flow<CustomizationOption?>,
        stagePack: (String?) -> Unit,
    ) {
        val context = view.context
        val client = IconPackClient(context)
        val currentRow: TextView = view.requireViewById(R.id.icon_pack_current)
        val wallpaperRow: LinearLayout = view.requireViewById(R.id.icon_pack_wallpapers)
        val wallpaperTitle: TextView = view.requireViewById(R.id.icon_pack_wallpapers_title)
        val wallpaperScroll: View = view.requireViewById(R.id.icon_pack_wallpapers_scroll)
        val importButton: Button = view.requireViewById(R.id.icon_pack_import_button)

        var packs: List<IconPackClient.Pack> = emptyList()
        var appliedFile = ""
        var stagedFile = ""

        fun labelFor(file: String): String =
            if (file.isEmpty()) context.getString(R.string.icon_pack_system)
            else packs.firstOrNull { it.fileName == file }?.label ?: file

        fun showWallpapers(packFile: String) {
            lifecycleOwner.lifecycleScope.launch {
                val names =
                    if (packFile.isEmpty()) emptyList()
                    else withContext(Dispatchers.IO) { client.getWallpaperNames(packFile) }
                val visible = names.isNotEmpty()
                wallpaperTitle.visibility = if (visible) View.VISIBLE else View.GONE
                wallpaperScroll.visibility = if (visible) View.VISIBLE else View.GONE
                wallpaperRow.removeAllViews()
                for (name in names) {
                    val thumb = withContext(Dispatchers.IO) { client.loadWallpaper(packFile, name) }
                    wallpaperRow.addView(
                        wallpaperOption(context, thumb) {
                            applyWallpaper(context, client, lifecycleOwner, packFile, name)
                        }
                    )
                }
            }
        }

        fun refresh() {
            lifecycleOwner.lifecycleScope.launch {
                packs = withContext(Dispatchers.IO) { client.getPacks() }
                appliedFile = packs.firstOrNull { it.selected }?.fileName ?: ""
                stagedFile = appliedFile
                currentRow.text = labelFor(appliedFile)
                stagePack(null)
                showWallpapers(appliedFile)
            }
        }

        currentRow.setOnClickListener {
            val files = ArrayList<String>()
            val labels = ArrayList<String>()
            files.add("")
            labels.add(context.getString(R.string.icon_pack_system))
            for (pack in packs) {
                files.add(pack.fileName)
                labels.add(pack.label)
            }
            AlertDialog.Builder(context)
                .setTitle(R.string.icon_pack_section_packs)
                .setSingleChoiceItems(
                    labels.toTypedArray(),
                    files.indexOf(stagedFile).coerceAtLeast(0),
                ) { dialog, which ->
                    dialog.dismiss()
                    stagedFile = files[which]
                    currentRow.text = labelFor(stagedFile)
                    stagePack(if (stagedFile == appliedFile) null else stagedFile)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        importButton.setOnClickListener {
            context.startActivity(
                Intent(context, IconPackImportActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        refresh()

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selectedOption.collect { option ->
                    if (option !== ThemePickerHomeCustomizationOption.ICON_PACK) refresh()
                }
            }
        }
    }

    private fun applyWallpaper(
        context: Context,
        client: IconPackClient,
        lifecycleOwner: LifecycleOwner,
        packFile: String,
        name: String,
    ) {
        lifecycleOwner.lifecycleScope.launch {
            val applied =
                withContext(Dispatchers.IO) {
                    val bitmap = client.loadWallpaper(packFile, name)
                    if (bitmap == null) {
                        false
                    } else {
                        try {
                            WallpaperManager.getInstance(context).setBitmap(bitmap)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }
                }
            Toast.makeText(
                    context,
                    if (applied) R.string.icon_pack_wallpaper_applied
                    else R.string.icon_pack_wallpaper_failed,
                    Toast.LENGTH_SHORT,
                )
                .show()
        }
    }

    private fun wallpaperOption(context: Context, thumb: Bitmap?, onClick: () -> Unit): View {
        val density = context.resources.displayMetrics.density
        val width = (density * 72).toInt()
        val height = (density * 128).toInt()
        val margin = (density * 6).toInt()
        return ImageView(context).apply {
            layoutParams =
                LinearLayout.LayoutParams(width, height).apply { setMargins(margin, 0, margin, 0) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            if (thumb != null) setImageBitmap(thumb)
            setOnClickListener { onClick() }
        }
    }
}
