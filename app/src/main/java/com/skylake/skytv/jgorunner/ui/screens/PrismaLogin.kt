package com.skylake.skytv.jgorunner.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skylake.skytv.jgorunner.data.SkySharedPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private enum class LoginStep { Phone, Code, Done }

/** PrismA TV sign-in. Same name/signature as before, so navigation is unchanged. */
@Composable
fun LoginScreen(context: Context) = PrismaSignIn(context)

/**
 * Shared sign-in UI. [onDone] adds a Continue button after signing in and
 * [onSkip] adds "Skip for now" (both used by the first-run setup).
 */
@Composable
fun PrismaSignIn(context: Context, onDone: (() -> Unit)? = null, onSkip: (() -> Unit)? = null) {
    val prefs = remember { SkySharedPref.getInstance(context) }
    val base = "http://localhost:${prefs.myPrefs.jtvGoServerPort}"
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(LoginStep.Phone) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var serverUp by remember { mutableStateOf<Boolean?>(null) }
    var resendIn by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { serverUp = withContext(Dispatchers.IO) { ping(base) } }
    LaunchedEffect(resendIn) { if (resendIn > 0) { delay(1000); resendIn-- } }

    fun sendCode() {
        val digits = phone.filter { it.isDigit() }
        if (digits.length != 10) { isError = true; message = "Enter your 10-digit Jio number."; return }
        busy = true; isError = false; message = "Sending code…"
        scope.launch {
            val res = withContext(Dispatchers.IO) { post("$base/login/sendOTP", """{"number":"+91$digits"}""") }
            busy = false
            if (res != null && res.contains("true", ignoreCase = true)) {
                message = null; code = ""; resendIn = 30; step = LoginStep.Code
            } else {
                isError = true
                message = if (res == null) "Couldn’t reach the server. Start it, then try again."
                else "The code wasn’t sent. Check the number and try again."
            }
        }
    }

    fun verify() {
        val digits = phone.filter { it.isDigit() }
        if (code.length < 4) { isError = true; message = "Enter the code from your messages."; return }
        busy = true; isError = false; message = "Checking code…"
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                post("$base/login/verifyOTP", """{"number":"+91$digits","otp":"$code"}""")
            }
            busy = false
            val ok = runCatching { JSONObject(res ?: "").optString("status") == "success" }.getOrDefault(false)
            if (ok) { message = null; step = LoginStep.Done }
            else { isError = true; message = "That code didn’t work. Check it and try again." }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Prisma.Bg)
            .background(Brush.radialGradient(listOf(Color(0x29AAB6C8), Color.Transparent), radius = 1200f))
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(72.dp, Alignment.CenterHorizontally)
        ) {
            // Brand side
            Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(
                    Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF23262E), Color(0xFF0D0E12))))
                        .border(1.5.dp, Prisma.Edge, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) { PrismaGem(72.dp) }
                PrismaWordmark(56.sp)
                Text(
                    "Live TV, beautifully simple. Sign in once with your Jio number and you’re set.",
                    color = Prisma.Muted, fontSize = 18.sp, lineHeight = 27.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrismaStatusDot(
                        when (serverUp) { true -> Prisma.Green; false -> Prisma.Red; null -> Prisma.Faint }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when (serverUp) {
                            true -> "Server ready on this device"
                            false -> "Server isn’t running. Start it from the home screen."
                            null -> "Checking server…"
                        },
                        color = Prisma.Faint, fontSize = 14.sp
                    )
                }
            }

            // Glass card
            Column(
                Modifier
                    .width(500.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Prisma.Card)
                    .border(1.5.dp, Prisma.Edge, RoundedCornerShape(28.dp))
                    .padding(36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Bar(true); Spacer(Modifier.width(10.dp)); Bar(step != LoginStep.Phone)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (step == LoginStep.Done) "DONE" else "STEP ${if (step == LoginStep.Phone) 1 else 2} OF 2",
                        color = Prisma.Faint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp
                    )
                }

                AnimatedContent(
                    targetState = step,
                    transitionSpec = { (fadeIn() + slideInVertically { it / 8 }) togetherWith fadeOut() },
                    label = "loginStep"
                ) { s ->
                    when (s) {
                        LoginStep.Phone -> PhoneStep(phone, { phone = it.filter { c -> c.isDigit() }.take(10) }, busy, ::sendCode, onSkip)
                        LoginStep.Code -> CodeStep(
                            phone = phone, code = code,
                            onCode = { code = it.filter { c -> c.isDigit() }.take(6); if (code.length == 6) verify() },
                            busy = busy, resendIn = resendIn,
                            onVerify = ::verify,
                            onChange = { step = LoginStep.Phone; message = null },
                            onResend = { if (resendIn == 0) sendCode() }
                        )
                        LoginStep.Done -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Prisma.Green, modifier = Modifier.size(56.dp))
                            Text("You’re signed in", color = Prisma.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            if (onDone != null) PrimaryButton("Continue", onDone)
                            else Text("Press Back, then Watch TV.", color = Prisma.Muted, fontSize = 17.sp)
                        }
                    }
                }

                message?.let {
                    Text(it, color = if (isError) Prisma.Red else Prisma.Muted, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun Bar(on: Boolean) {
    Box(
        Modifier
            .width(56.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (on) Prisma.Silver else Color(0x26FFFFFF))
    )
}

@Composable
private fun PhoneStep(phone: String, onPhone: (String) -> Unit, busy: Boolean, onSend: () -> Unit, onSkip: (() -> Unit)?) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Sign in", color = Prisma.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Enter the Jio number that has your TV plan. We’ll text you a code.", color = Prisma.Muted, fontSize = 16.sp, lineHeight = 24.sp)
        Text("JIO NUMBER", color = Prisma.Faint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x99080810))
                .border(if (focused) 2.dp else 1.5.dp, if (focused) Prisma.Silver else Prisma.Edge, RoundedCornerShape(18.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("+91", color = Prisma.Muted, fontSize = 22.sp, modifier = Modifier.padding(start = 20.dp, end = 14.dp))
            BasicTextField(
                value = phone,
                onValueChange = onPhone,
                singleLine = true,
                textStyle = TextStyle(color = Prisma.Ink, fontSize = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp),
                cursorBrush = SolidColor(Prisma.Silver),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }, onDone = { onSend() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused }
            )
        }
        PrimaryButton(if (busy) "Sending…" else "Send code", onSend)
        Text("Use the remote’s number keys or the on-screen keyboard.", color = Prisma.Faint, fontSize = 13.sp)
        if (onSkip != null) {
            PrismaFocusBox(onClick = onSkip, shape = RoundedCornerShape(10.dp),
                background = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), restingEdge = Color.Transparent) {
                Text("Skip for now", color = Prisma.Faint, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun CodeStep(
    phone: String, code: String, onCode: (String) -> Unit, busy: Boolean, resendIn: Int,
    onVerify: () -> Unit, onChange: () -> Unit, onResend: () -> Unit
) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Enter the code", color = Prisma.Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sent to +91 $phone  ", color = Prisma.Muted, fontSize = 16.sp)
            PrismaFocusBox(onClick = onChange, shape = RoundedCornerShape(10.dp)) {
                Text("Change", color = Prisma.Silver, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        BasicTextField(
            value = code,
            onValueChange = onCode,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onVerify() }),
            modifier = Modifier
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { i ->
                        val active = focused && i == code.length.coerceAtMost(5)
                        Box(
                            Modifier
                                .size(width = 60.dp, height = 72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x99080810))
                                .border(
                                    if (active) 2.dp else 1.5.dp,
                                    if (active) Prisma.Silver else Prisma.Edge,
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(code.getOrNull(i)?.toString() ?: "", color = Prisma.Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        )
        PrimaryButton(if (busy) "Checking…" else "Sign in", onVerify)
        PrismaFocusBox(onClick = onResend, shape = RoundedCornerShape(10.dp),
            background = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)), restingEdge = Color.Transparent) {
            Text(
                if (resendIn > 0) "Didn’t get it? Resend in 0:${resendIn.toString().padStart(2, '0')}" else "Resend code",
                color = Prisma.Faint, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    PrismaFocusBox(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        shape = RoundedCornerShape(18.dp),
        background = Prisma.SilverFill,
        restingEdge = Color(0x40FFFFFF),
        focusScale = 1.02f
    ) {
        Text(label, color = Prisma.Bg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

private fun ping(base: String): Boolean = runCatching {
    (URL(base).openConnection() as HttpURLConnection).run {
        connectTimeout = 1500; readTimeout = 1500; requestMethod = "GET"
        val ok = responseCode in 200..499
        disconnect(); ok
    }
}.getOrDefault(false)

private fun post(url: String, json: String): String? = runCatching {
    (URL(url).openConnection() as HttpURLConnection).run {
        requestMethod = "POST"
        connectTimeout = 8000; readTimeout = 15000
        setRequestProperty("Content-Type", "application/json")
        doOutput = true
        outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val stream = if (responseCode in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        disconnect(); body
    }
}.getOrNull()
