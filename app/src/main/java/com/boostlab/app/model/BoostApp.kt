package com.boostlab.app.model

import android.graphics.Bitmap

data class BoostApp(
    val label: String,
    val packageName: String,
    val icon: Bitmap? = null,
)
