package com.skylake.skytv.jgorunner.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** PrismA TV look: graphite ground, silver/glass surfaces. */
object Prisma {
    val Bg = Color(0xFF0B0B0F)
    val Card = Color(0x8C1C1E26)
    val CardSolid = Color(0xF2161820)
    val Glass = Color(0x0FFFFFFF)
    val Edge = Color(0x33D6DEE8)
    val Silver = Color(0xFFE6EBF0)
    val Ink = Color(0xFFF4F4F5)
    val Muted = Color(0xFFA1A1AA)
    val Faint = Color(0xFF8E98A4)
    val Green = Color(0xFF6EE7B7)
    val Red = Color(0xFFFCA5A5)
    val SilverFill = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFC9D0D8)))
    val Chrome = Brush.linearGradient(
        listOf(
            Color(0xFFFFFFFF), Color(0xFF8E98A4), Color(0xFFF1F4F7),
            Color(0xFF6B7480), Color(0xFFDFE5EB)
        )
    )
}

/** The four-facet silver gem logo. */
@Composable
fun PrismaGem(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val s = w / 100f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val top = p(50f, 8f); val right = p(90f, 50f); val bottom = p(50f, 92f)
        val left = p(10f, 50f); val mid = p(50f, 50f)
        fun tri(a: Offset, b: Offset, c: Offset) = Path().apply {
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
        }
        val outline = Path().apply {
            moveTo(top.x, top.y); lineTo(right.x, right.y)
            lineTo(bottom.x, bottom.y); lineTo(left.x, left.y); close()
        }
        drawPath(outline, Brush.verticalGradient(listOf(Color(0xFF8B95A1), Color(0xFF2F343C))))
        drawPath(tri(top, mid, left), Brush.linearGradient(listOf(Color.White, Color(0xFF9AA3AD)), top, mid))
        drawPath(tri(top, right, mid), Brush.linearGradient(listOf(Color(0xFFC7CFD8), Color(0xFF5D6672)), right, mid))
        drawPath(tri(left, mid, bottom), Color(0xFF4A525D))
        drawPath(
            outline,
            Brush.verticalGradient(listOf(Color(0x61FFFFFF), Color(0x0FFFFFFF), Color.Transparent))
        )
        drawLine(Color(0x59FFFFFF), top, bottom, strokeWidth = 0.9f * s)
        drawLine(Color(0x59FFFFFF), left, right, strokeWidth = 0.9f * s)
        drawPath(outline, Prisma.Chrome, style = Stroke(width = 4.2f * s, join = StrokeJoin.Round))
    }
}

/** "PrismA TV" wordmark with the bold final A. */
@Composable
fun PrismaWordmark(fontSize: TextUnit = 28.sp, color: Color = Prisma.Ink) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Prism") }
            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("A") }
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = fontSize * 0.45f,
                    letterSpacing = 3.sp,
                    color = Prisma.Faint
                )
            ) { append("  TV") }
        },
        fontSize = fontSize,
        color = color
    )
}

/**
 * Focusable glass surface for TV remotes: a silver ring and a slight
 * lift when focused, works with OK/Enter and touch.
 */
@Composable
fun PrismaFocusBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    background: Brush = Brush.linearGradient(listOf(Prisma.Glass, Prisma.Glass)),
    restingEdge: Color = Prisma.Edge,
    focusScale: Float = 1.03f,
    content: @Composable (focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focusScale")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(background, shape)
            .border(
                width = if (focused) 2.5.dp else 1.5.dp,
                brush = if (focused) Prisma.Chrome else Brush.linearGradient(listOf(restingEdge, restingEdge)),
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content(focused) }
}

@Composable
fun PrismaStatusDot(color: Color, modifier: Modifier = Modifier) {
    Row(modifier) {
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}
