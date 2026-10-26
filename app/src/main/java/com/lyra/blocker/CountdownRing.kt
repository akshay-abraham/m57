// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/** A quiet expressive clock: visual frames are independent of the actual redirect deadline. */
class CountdownRing(context:Context):View(context){
    var fraction=1f;set(value){val next=value.coerceIn(0f,1f);if(field!=next){field=next;invalidate()}}
    var seconds=0;set(value){if(field!=value){field=value;caption="${value}s";invalidate()}}
    private var caption="0s"
    private val arcBounds=RectF()
    private val textFace=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    init{minimumHeight=LyraUi.dp(context,150);importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        val density=resources.displayMetrics.density
        val radius=minOf(width/2f-12*density,58*density).coerceAtLeast(0f)
        val cx=width/2f;val cy=height/2f
        paint.style=Paint.Style.STROKE;paint.strokeWidth=7*density;paint.strokeCap=Paint.Cap.ROUND
        paint.color=android.graphics.Color.rgb(247,219,197);canvas.drawCircle(cx,cy,radius,paint)
        arcBounds.set(cx-radius,cy-radius,cx+radius,cy+radius)
        paint.color=LyraUi.orange;canvas.drawArc(arcBounds,-90f,-360*fraction,false,paint)
        paint.style=Paint.Style.FILL;paint.color=LyraUi.ink;paint.typeface=textFace
        paint.textSize=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,32f,resources.displayMetrics);paint.textAlign=Paint.Align.CENTER
        canvas.drawText(caption,cx,cy-(paint.ascent()+paint.descent())/2f,paint)
    }
}
