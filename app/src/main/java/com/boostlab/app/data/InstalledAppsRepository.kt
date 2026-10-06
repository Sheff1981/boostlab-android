package com.boostlab.app.data

import android.content.Context
import android.content.Intent
import com.boostlab.app.model.BoostApp

class InstalledAppsRepository(private val context: Context) {
    fun loadLaunchableApps(): List<BoostApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { info ->
                BoostApp(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.activityInfo.packageName,
                )
            }
            .filterNot { it.packageName == context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
