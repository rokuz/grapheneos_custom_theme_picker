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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.android.themepicker.R
import java.util.concurrent.Executors

class IconPackImportActivity : Activity() {

    private val background = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/vnd.android.package-archive"),
                REQUEST_IMPORT,
            )
        }
    }

    override fun onDestroy() {
        background.shutdown()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_IMPORT) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            finish()
            return
        }
        val appContext = applicationContext
        background.execute {
            val label = IconPackClient(appContext).importPack(uri)
            runOnUiThread {
                if (label == null) {
                    Toast.makeText(appContext, R.string.icon_pack_import_failed, Toast.LENGTH_SHORT)
                        .show()
                } else {
                    Toast.makeText(
                            appContext,
                            appContext.getString(R.string.icon_pack_imported, label),
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_IMPORT = 2602
    }
}
