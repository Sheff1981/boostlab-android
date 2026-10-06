package com.boostlab.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.core.graphics.drawable.toBitmap
import com.boostlab.app.model.BoostApp

class InstalledAppsRepository(private val context: Context) {
    fun loadLaunchableApps(): List<BoostApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { info ->
                val icon = runCatching {
                    info.loadIcon(pm).toBitmap(
                        width = ICON_SIZE_PX,
                        height = ICON_SIZE_PX,
                    )
                }.getOrNull()

                val applicationInfo = info.activityInfo.applicationInfo
                val isGame =
                    applicationInfo.category == ApplicationInfo.CATEGORY_GAME ||
                        applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME != 0

                AppCandidate(
                    app = BoostApp(
                        label = info.loadLabel(pm).toString(),
                        packageName = info.activityInfo.packageName,
                        icon = icon,
                    ),
                    isGame = isGame,
                )
            }
            .filterNot { it.app.packageName == context.packageName }
            .distinctBy { it.app.packageName }
            .sortedWith(
                compareByDescending<AppCandidate> { it.isGame }
                    .thenBy { it.app.label.lowercase() },
            )
            .map { it.app }
            .toList()
    }

    private data class AppCandidate(
        val app: BoostApp,
        val isGame: Boolean,
    )

    companion object {
        private const val ICON_SIZE_PX = 192
    }
}
