// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/** A quiet expressive clock: visual frames are independent of the actual redirect deadline. */
class CountdownRing(context:Context):View(context){
    var fraction=1f;set(value){field=value.coerceIn(0f,1f);invalidate()}
    var seconds=0;set(value){field=value;invalidate()}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init{minimumHeight=LyraUi.dp(context,150);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        val density=resources.displayMetrics.density
        val radius=minOf(width/2f-12*density,58*density)
        val cx=width/2f;val cy=height/2f
        paint.style=Paint.Style.STROKE;paint.strokeWidth=7*density;paint.strokeCap=Paint.Cap.ROUND
        paint.color=android.graphics.Color.rgb(247,219,197);canvas.drawCircle(cx,cy,radius,paint)
        paint.color=LyraUi.orange;canvas.drawArc(RectF(cx-radius,cy-radius,cx+radius,cy+radius),-90f,-360*fraction,false,paint)
        paint.style=Paint.Style.FILL;paint.color=LyraUi.ink;paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        paint.textSize=32*resources.displayMetrics.scaledDensity;paint.textAlign=Paint.Align.CENTER
        canvas.drawText("${seconds}s",cx,cy-(paint.ascent()+paint.descent())/2f,paint)
    }
}
