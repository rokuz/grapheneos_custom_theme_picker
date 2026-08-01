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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log

class IconPackClient(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    data class Pack(val fileName: String, val label: String, val selected: Boolean)

    fun getPacks(): List<Pack> {
        val packs = ArrayList<Pack>()
        try {
            resolver.query(uriFor(PATH_PACKS), null, null, null, null)?.use { cursor ->
                val fileIdx = cursor.getColumnIndexOrThrow(COLUMN_FILE)
                val labelIdx = cursor.getColumnIndexOrThrow(COLUMN_LABEL)
                val selectedIdx = cursor.getColumnIndexOrThrow(COLUMN_SELECTED)
                while (cursor.moveToNext()) {
                    packs.add(
                        Pack(
                            cursor.getString(fileIdx),
                            cursor.getString(labelIdx),
                            cursor.getInt(selectedIdx) == 1,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to query icon packs", e)
        }
        return packs
    }

    fun getIconNames(packFile: String): List<String> = names(PATH_ICONS, packFile)

    fun getWallpaperNames(packFile: String): List<String> = names(PATH_WALLPAPERS, packFile)

    fun loadIcon(packFile: String, name: String): Bitmap? = bitmap(PATH_ICON, packFile, name)

    fun loadWallpaper(packFile: String, name: String): Bitmap? =
        bitmap(PATH_WALLPAPER, packFile, name)

    fun selectPack(packFile: String) {
        callProvider(METHOD_SELECT_PACK, Bundle().apply { putString(EXTRA_PACK, packFile) })
    }

    fun deletePack(packFile: String) {
        callProvider(METHOD_DELETE_PACK, Bundle().apply { putString(EXTRA_PACK, packFile) })
    }

    fun importPack(source: Uri): String? {
        val stagedName = "import-${System.currentTimeMillis()}.apk"
        try {
            resolver.openInputStream(source).use { input ->
                if (input == null) return null
                resolver.openOutputStream(uriFor(PATH_IMPORT, stagedName), "w").use { output ->
                    if (output == null) return null
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to stage icon pack", e)
            return null
        }
        val result =
            callProvider(METHOD_FINISH_IMPORT, Bundle().apply { putString(EXTRA_PACK, stagedName) })
        return result?.getString(EXTRA_LABEL)
    }

    private fun names(path: String, packFile: String): List<String> {
        val names = ArrayList<String>()
        try {
            resolver.query(uriFor(path, packFile), null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndexOrThrow(COLUMN_NAME)
                while (cursor.moveToNext()) {
                    names.add(cursor.getString(idx))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to query $path", e)
        }
        return names
    }

    private fun bitmap(path: String, packFile: String, name: String): Bitmap? {
        return try {
            resolver.openInputStream(uriFor(path, packFile, name))?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun callProvider(method: String, extras: Bundle): Bundle? {
        return try {
            resolver.call(uriFor(PATH_PACKS), method, null, extras)
        } catch (e: Exception) {
            Log.w(TAG, "provider call $method failed", e)
            null
        }
    }

    private fun uriFor(vararg segments: String): Uri {
        val builder = Uri.Builder().scheme("content").authority(AUTHORITY)
        segments.forEach { builder.appendPath(it) }
        return builder.build()
    }

    companion object {
        private const val TAG = "IconPackClient"
        private const val AUTHORITY = "com.android.launcher3.iconpacks"

        private const val PATH_PACKS = "packs"
        private const val PATH_ICONS = "icons"
        private const val PATH_WALLPAPERS = "wallpapers"
        private const val PATH_ICON = "icon"
        private const val PATH_WALLPAPER = "wallpaper"
        private const val PATH_IMPORT = "import"

        private const val COLUMN_FILE = "file"
        private const val COLUMN_LABEL = "label"
        private const val COLUMN_SELECTED = "selected"
        private const val COLUMN_NAME = "name"

        private const val METHOD_SELECT_PACK = "select_pack"
        private const val METHOD_DELETE_PACK = "delete_pack"
        private const val METHOD_FINISH_IMPORT = "finish_import"

        private const val EXTRA_PACK = "pack"
        private const val EXTRA_LABEL = "label"
    }
}
