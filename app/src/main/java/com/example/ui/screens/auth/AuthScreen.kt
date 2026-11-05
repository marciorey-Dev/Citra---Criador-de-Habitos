package com.example.ui.screens.auth

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.data.auth.AuthRepository
import com.example.ui.components.CitraMascote
import com.example.ui.components.MascotHumor
import com.example.ui.theme.RedAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CitraIntroOrange = Color(0xFFFF8B1F)
private val CitraShadowColor = Color(0xFF2B1A12)

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var isSignUp by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isNameFocused by remember { mutableStateOf(false) }
    var isUsernameFocused by remember { mutableStateOf(false) }
    var isPasswordFocused by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showGuestWarningDialog by remember { mutableStateOf(false) }

    // Always play full-screen mascot intro on entering AuthScreen
    val isFirstLaunch = true
    val reduceAnimations = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    // Mascot state & triggers
    val mascotSizeDp = 112.dp
    val saturationDistPx = remember(density) {
        with(density) { (110.dp * (mascotSizeDp.value / 200f)).toPx() }
    }
    var mascotCenterInRoot by remember { mutableStateOf(Offset.Unspecified) }
    var headerMascotCenterInRoot by remember { mutableStateOf(Offset.Unspecified) }
    var rootPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var rootCenterYInRoot by remember { mutableFloatStateOf(0f) }

    var touchOlharX by remember { mutableFloatStateOf(0f) }
    var touchOlharY by remember { mutableFloatStateOf(0f) }

    var nodTrigger by remember { mutableIntStateOf(0) }
    var shakeTrigger by remember { mutableIntStateOf(0) }
    var jumpTrigger by remember { mutableIntStateOf(0) }

    var tempHumor by remember { mutableStateOf<MascotHumor?>(null) }
    var tempHumorJob by remember { mutableStateOf<Job?>(null) }

    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isIdleSleepy by remember { mutableStateOf(false) }

    // Intro animation states
    var introActive by remember { mutableStateOf(!reduceAnimations) }
    val orangeBgAlpha = remember { Animatable(if (isFirstLaunch && !reduceAnimations) 1f else 0f) }
    val introScale = remember { Animatable(if (isFirstLaunch && !reduceAnimations) 4.2f else 1f) }
    val introScaleX = remember { Animatable(1f) }
    val introScaleY = remember { Animatable(1f) }
    val introOffsetYPx = remember { Animatable(0f) }
    val shadowAlpha = remember { Animatable(0f) }
    val shadowScale = remember { Animatable(1f) }

    // Staggered form entrance alphas (0..1)
    val titleProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val subtitleProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val toggleGoogleProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val emailProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val passwordProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val loginBtnProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }
    val guestProgress = remember { Animatable(if (reduceAnimations) 1f else 0f) }

    // Run intro timeline
    LaunchedEffect(Unit) {
        if (reduceAnimations) {
            introActive = false
            return@LaunchedEffect
        }

        // Wait briefly for layout measurement of headerMascotCenterInRoot
        var waitFrames = 0
        while (headerMascotCenterInRoot == Offset.Unspecified && waitFrames < 20) {
            delay(16L)
            waitFrames++
        }

        val targetOffsetY = if (headerMascotCenterInRoot != Offset.Unspecified && rootCenterYInRoot > 0f) {
            headerMascotCenterInRoot.y - rootCenterYInRoot
        } else {
            with(density) { -220.dp.toPx() }
        }

        if (isFirstLaunch) {
            // 0.0 - 1.8s: Full orange screen, 4x zoomed face blinking
            delay(1800L)

            // 1.8 - 3.2s: Zoom out (4.2 -> 1.0) & fade out orange background (2.0 - 2.8s)
            launch {
                introScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
                )
            }
            launch {
                delay(200L)
                orangeBgAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                )
            }
            launch {
                delay(900L)
                shadowAlpha.animateTo(0.22f, tween(350))
            }

            delay(1500L) // Total ~3.3s

            // 3.3 - 4.6s: Two ground bounces with squash & stretch
            val bounce1Height = with(density) { 78.dp.toPx() }
            val bounce2Height = with(density) { 36.dp.toPx() }

            // Bounce 1
            launch { introScaleX.animateTo(1.10f, tween(70)); introScaleY.animateTo(0.88f, tween(70)) }
            delay(70L)
            launch {
                introScaleX.animateTo(0.94f, tween(250, easing = FastOutSlowInEasing))
                introScaleY.animateTo(1.07f, tween(250, easing = FastOutSlowInEasing))
                shadowScale.animateTo(0.45f, tween(250))
                shadowAlpha.animateTo(0.10f, tween(250))
            }
            introOffsetYPx.animateTo(-bounce1Height, tween(250, easing = FastOutSlowInEasing))

            launch {
                shadowScale.animateTo(1.08f, tween(210))
                shadowAlpha.animateTo(0.26f, tween(210))
            }
            introOffsetYPx.animateTo(0f, tween(210, easing = FastOutLinearInEasing))

            launch { introScaleX.animateTo(1.10f, tween(100)); introScaleY.animateTo(0.88f, tween(100)) }
            delay(100L)
            launch { introScaleX.animateTo(1f, tween(90)); introScaleY.animateTo(1f, tween(90)) }
            delay(90L)

            // Bounce 2 (smaller)
            launch {
                introScaleX.animateTo(0.96f, tween(180, easing = FastOutSlowInEasing))
                introScaleY.animateTo(1.04f, tween(180, easing = FastOutSlowInEasing))
                shadowScale.animateTo(0.68f, tween(180))
                shadowAlpha.animateTo(0.15f, tween(180))
            }
            introOffsetYPx.animateTo(-bounce2Height, tween(180, easing = FastOutSlowInEasing))

            launch {
                shadowScale.animateTo(1f, tween(160))
                shadowAlpha.animateTo(0.22f, tween(160))
            }
            introOffsetYPx.animateTo(0f, tween(160, easing = FastOutLinearInEasing))

            launch { introScaleX.animateTo(1.06f, tween(80)); introScaleY.animateTo(0.92f, tween(80)) }
            delay(80L)
            launch { introScaleX.animateTo(1f, tween(80)); introScaleY.animateTo(1f, tween(80)) }
            delay(100L)

            // 4.7 - 5.7s: Rise from center to final header position with 12dp overshoot bounce
            launch { shadowAlpha.animateTo(0f, tween(200)) }

            val overshootPx = with(density) { 12.dp.toPx() }
            launch {
                delay(550L)
                // Staggered form entrance (5.4s - 7.2s)
                launch { titleProgress.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
                delay(100L)
                launch { subtitleProgress.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
                delay(200L)
                launch { toggleGoogleProgress.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
                delay(300L)
                launch { emailProgress.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
                delay(300L)
                launch { passwordProgress.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
                delay(300L)
                launch { loginBtnProgress.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
                delay(300L)
                launch { guestProgress.animateTo(1f, tween(550, easing = FastOutSlowInEasing)) }
            }

            introOffsetYPx.animateTo(
                targetValue = targetOffsetY - overshootPx,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
            introOffsetYPx.animateTo(
                targetValue = targetOffsetY,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
            introActive = false
        } else {
            // Subsequent launches: short ~1.5s version
            delay(550L)
            val overshootPx = with(density) { 8.dp.toPx() }
            launch {
                delay(250L)
                launch { titleProgress.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                delay(60L)
                launch { subtitleProgress.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                delay(60L)
                launch { toggleGoogleProgress.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                delay(60L)
                launch { emailProgress.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                delay(60L)
                launch { passwordProgress.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                delay(60L)
                launch { loginBtnProgress.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                delay(60L)
                launch { guestProgress.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
            }
            introOffsetYPx.animateTo(
                targetValue = targetOffsetY - overshootPx,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
            )
            introOffsetYPx.animateTo(
                targetValue = targetOffsetY,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
            introActive = false
        }
    }

    LaunchedEffect(lastInteractionTime) {
        isIdleSleepy = false
        delay(12_000L)
        isIdleSleepy = true
    }

    fun triggerTempHumor(humor: MascotHumor, durationMs: Long) {
        lastInteractionTime = System.currentTimeMillis()
        tempHumorJob?.cancel()
        tempHumor = humor
        tempHumorJob = coroutineScope.launch {
            delay(durationMs)
            tempHumor = null
        }
    }

    val googleInteractionSource = remember { MutableInteractionSource() }
    val primaryInteractionSource = remember { MutableInteractionSource() }
    val guestInteractionSource = remember { MutableInteractionSource() }

    val isGooglePressed by googleInteractionSource.collectIsPressedAsState()
    val isPrimaryPressed by primaryInteractionSource.collectIsPressedAsState()
    val isGuestPressed by guestInteractionSource.collectIsPressedAsState()
    val isAnyButtonPressed = isGooglePressed || isPrimaryPressed || isGuestPressed

    LaunchedEffect(isAnyButtonPressed) {
        if (isAnyButtonPressed) {
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    // Priority order (highest to lowest):
    // 1. Temporary humor (NAO on error, FELIZ on Google/Guest/valid login)
    // 2. Button pressed (FELIZ)
    // 3. Password visible (DISCRETO, olharX = -1f, olharY = -0.75f)
    // 4. Field in focus / typing (looks down at active field & tracks text)
    // 5. Idle 12s (SONOLENTO)
    // 6. Follow pointer/touch (returns to 0f, 0f when released)
    val (mascotHumor, mascotOlharX, mascotOlharY) = when {
        tempHumor != null -> Triple(tempHumor!!, 0f, 0f)
        isAnyButtonPressed -> Triple(MascotHumor.FELIZ, 0f, 0f)
        showPassword -> Triple(MascotHumor.DISCRETO, -1f, -0.75f)
        isPasswordFocused -> Triple(MascotHumor.NEUTRO, 0.1f, 0.95f)
        isNameFocused -> {
            val trackX = (name.length.coerceAtMost(24) / 24f) * 0.6f - 0.3f
            Triple(MascotHumor.NEUTRO, trackX, 0.58f)
        }
        isUsernameFocused -> {
            val trackX = (username.length.coerceAtMost(24) / 24f) * 0.6f - 0.3f
            Triple(MascotHumor.NEUTRO, trackX, if (isSignUp) 0.74f else 0.68f)
        }
        isIdleSleepy -> Triple(MascotHumor.SONOLENTO, 0f, 0f)
        else -> Triple(MascotHumor.NEUTRO, touchOlharX, touchOlharY)
    }

    val riseOffsetPx = remember(density) { with(density) { 10.dp.toPx() } }
    fun Modifier.staggeredEntrance(progress: Float): Modifier = this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * riseOffsetPx
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                rootPositionInRoot = pos
                rootCenterYInRoot = pos.y + coords.size.height / 2f
            }
            .pointerInput(saturationDistPx) {
                awaitEachGesture {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val firstChange = event.changes.firstOrNull()
                        if (firstChange != null && firstChange.pressed) {
                            lastInteractionTime = System.currentTimeMillis()
                            if (mascotCenterInRoot != Offset.Unspecified) {
                                val pointerInRoot = rootPositionInRoot + firstChange.position
                                val dx = pointerInRoot.x - mascotCenterInRoot.x
                                val dy = pointerInRoot.y - mascotCenterInRoot.y
                                touchOlharX = (dx / saturationDistPx).coerceIn(-1f, 1f)
                                touchOlharY = (dy / saturationDistPx).coerceIn(-1f, 1f)
                            }
                        }
                        if (event.changes.all { !it.pressed }) {
                            touchOlharX = 0f
                            touchOlharY = 0f
                            break
                        }
                    }
                }
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // Interactive Citra Mascot (hidden during intro overlay, visible seamlessly after)
                CitraMascote(
                    humor = mascotHumor,
                    olharX = mascotOlharX,
                    olharY = mascotOlharY,
                    tamanho = mascotSizeDp,
                    nodTrigger = nodTrigger,
                    shakeTrigger = shakeTrigger,
                    jumpTrigger = jumpTrigger,
                    modifier = Modifier
                        .alpha(if (introActive) 0f else 1f)
                        .onGloballyPositioned { coords ->
                            val center = coords.boundsInRoot().center
                            headerMascotCenterInRoot = center
                            if (!introActive) {
                                mascotCenterInRoot = center
                            }
                        }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Citra",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.staggeredEntrance(titleProgress.value)
                )

                Text(
                    text = "Foco, Hábitos e Produtividade com Backup na Nuvem",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .staggeredEntrance(subtitleProgress.value)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Toggle Entrar / Criar Conta
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(toggleGoogleProgress.value)
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        Button(
                            onClick = {
                                isSignUp = false
                                errorMessage = null
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isSignUp) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (!isSignUp) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Entrar", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                isSignUp = true
                                errorMessage = null
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSignUp) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isSignUp) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Criar Conta", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Google Sign-In Button
                OutlinedButton(
                    onClick = {
                        jumpTrigger++
                        triggerTempHumor(MascotHumor.FELIZ, 1500L)
                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            val result = authRepository.signInWithGoogle(context)
                            isLoading = false
                            if (result.isSuccess) {
                                onAuthSuccess()
                            } else {
                                val ex = result.exceptionOrNull()
                                if (ex is GetCredentialCancellationException) {
                                    Log.w("AuthScreen", "Google Sign-In cancelado ou fechado pelo usuário: ${ex.message}")
                                    errorMessage = null
                                } else {
                                    shakeTrigger++
                                    triggerTempHumor(MascotHumor.NAO, 1000L)
                                    errorMessage = ex?.localizedMessage ?: "Falha ao entrar com o Google"
                                }
                            }
                        }
                    },
                    interactionSource = googleInteractionSource,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("google_signin_button")
                        .staggeredEntrance(toggleGoogleProgress.value),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("G", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Continuar com o Google", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(emailProgress.value),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = " ou com usuário e senha ",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Form Fields
                if (isSignUp) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            lastInteractionTime = System.currentTimeMillis()
                            nodTrigger++
                        },
                        label = { Text("Seu Nome") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntrance(emailProgress.value)
                            .onFocusChanged {
                                isNameFocused = it.isFocused
                                lastInteractionTime = System.currentTimeMillis()
                            }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            lastInteractionTime = System.currentTimeMillis()
                            nodTrigger++
                        },
                        label = { Text("Nome de Usuário (@usuario)") },
                        placeholder = { Text("@exemplo") },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntrance(emailProgress.value)
                            .onFocusChanged {
                                isUsernameFocused = it.isFocused
                                lastInteractionTime = System.currentTimeMillis()
                            }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            lastInteractionTime = System.currentTimeMillis()
                            nodTrigger++
                        },
                        label = { Text("Usuário (@) ou E-mail") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredEntrance(emailProgress.value)
                            .onFocusChanged {
                                isUsernameFocused = it.isFocused
                                lastInteractionTime = System.currentTimeMillis()
                            }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        lastInteractionTime = System.currentTimeMillis()
                    },
                    label = { Text("Senha") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            showPassword = !showPassword
                            lastInteractionTime = System.currentTimeMillis()
                        }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(passwordProgress.value)
                        .onFocusChanged {
                            isPasswordFocused = it.isFocused
                            lastInteractionTime = System.currentTimeMillis()
                        }
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = RedAction,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (password.length < 6) {
                            shakeTrigger++
                            triggerTempHumor(MascotHumor.NAO, 1000L)
                            errorMessage = "A senha deve ter pelo menos 6 caracteres"
                            return@Button
                        }
                        isLoading = true
                        errorMessage = null
                        coroutineScope.launch {
                            val result = if (isSignUp) {
                                if (name.isBlank() || username.isBlank()) {
                                    isLoading = false
                                    shakeTrigger++
                                    triggerTempHumor(MascotHumor.NAO, 1000L)
                                    errorMessage = "Preencha todos os campos"
                                    return@launch
                                }
                                authRepository.createAccountWithEmailOrUsername(
                                    name = name.trim(),
                                    username = username.trim(),
                                    pass = password
                                )
                            } else {
                                if (username.isBlank()) {
                                    isLoading = false
                                    shakeTrigger++
                                    triggerTempHumor(MascotHumor.NAO, 1000L)
                                    errorMessage = "Informe seu usuário ou e-mail"
                                    return@launch
                                }
                                authRepository.signInWithEmailOrUsername(username.trim(), password)
                            }
                            isLoading = false
                            if (result.isSuccess) {
                                jumpTrigger++
                                triggerTempHumor(MascotHumor.FELIZ, 1500L)
                                onAuthSuccess()
                            } else {
                                shakeTrigger++
                                triggerTempHumor(MascotHumor.NAO, 1000L)
                                errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Erro na autenticação"
                            }
                        }
                    },
                    interactionSource = primaryInteractionSource,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .staggeredEntrance(loginBtnProgress.value),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (isSignUp) "Criar Minha Conta" else "Entrar no Citra", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Start Without Account (Guest Mode) with Notice
                TextButton(
                    onClick = {
                        jumpTrigger++
                        triggerTempHumor(MascotHumor.FELIZ, 1400L)
                        showGuestWarningDialog = true
                    },
                    interactionSource = guestInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredEntrance(guestProgress.value)
                ) {
                    Text(
                        text = "Começar sem conta (Modo Local)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Intro presentation overlay (full-screen orange -> zoom out -> 2 bounces -> rise to header)
            if (introActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CitraIntroOrange.copy(alpha = orangeBgAlpha.value)),
                    contentAlignment = Alignment.Center
                ) {
                    // Ground shadow during bounces
                    if (shadowAlpha.value > 0.005f) {
                        Canvas(
                            modifier = Modifier
                                .size(width = 76.dp, height = 16.dp)
                                .graphicsLayer {
                                    translationY = with(density) { (mascotSizeDp / 2 + 6.dp).toPx() }
                                    scaleX = shadowScale.value
                                    scaleY = shadowScale.value
                                    alpha = shadowAlpha.value
                                }
                        ) {
                            drawOval(
                                color = CitraShadowColor,
                                topLeft = Offset.Zero,
                                size = Size(size.width, size.height)
                            )
                        }
                    }

                    CitraMascote(
                        humor = mascotHumor,
                        olharX = mascotOlharX,
                        olharY = mascotOlharY,
                        tamanho = mascotSizeDp,
                        nodTrigger = nodTrigger,
                        shakeTrigger = shakeTrigger,
                        jumpTrigger = jumpTrigger,
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                if (introActive) {
                                    mascotCenterInRoot = coords.boundsInRoot().center
                                }
                            }
                            .graphicsLayer {
                                translationY = introOffsetYPx.value
                                scaleX = introScale.value * introScaleX.value
                                scaleY = introScale.value * introScaleY.value
                            }
                    )
                }
            }
        }
    }

    if (showGuestWarningDialog) {
        AlertDialog(
            onDismissRequest = { showGuestWarningDialog = false },
            icon = { Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
            title = { Text("Aviso de Sincronização", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Seus dados (hábitos, tarefas, notas e conquistas) não serão sincronizados na nuvem caso você use o app sem conta.\n\nPara ter suporte e backup contínuo, recomendamos vincular uma conta. Você poderá fazer isso a qualquer momento na aba 'Eu'.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGuestWarningDialog = false
                        authRepository.startGuestMode()
                        onContinueAsGuest()
                    }
                ) {
                    Text("Continuar Sem Conta")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuestWarningDialog = false }) {
                    Text("Voltar e Criar Conta")
                }
            }
        )
    }
}
