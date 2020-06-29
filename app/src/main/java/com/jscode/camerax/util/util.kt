package com.jscode.camerax.util

import android.animation.ObjectAnimator
import android.view.View

fun viewFadeIn(layout: View) {
    layout.visibility=View.VISIBLE
    ObjectAnimator.ofFloat(layout,View.ALPHA,1F).apply {
        start()
    }
}

fun viewFadeOut(layout: View) {
    ObjectAnimator.ofFloat(layout,View.ALPHA,0f).apply {
        start()
    }
    layout.visibility=View.GONE
}