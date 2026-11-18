package com.example.ui.screens.welcome

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.OnboardingProfileAnswers
import com.example.data.local.ThemeBrightnessChoice
import com.example.data.local.VisualMoodChoice
import com.example.ui.components.CitraMascote
import com.example.ui.components.MascotHumor
import com.example.ui.theme.ThemeAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val CitraHeroTop = Color(0xFFFFA53B)
private val CitraHeroMid = Color(0xFFFF8B1F)
private val CitraHeroBottom = Color(0xFFE65A00)

@Composable
fun WelcomeIntroScreen(
    initialAnswers: OnboardingProfileAnswers = OnboardingProfileAnswers(),
    onPreviewThemeMode: (Boolean?) -> Unit = {},
    onPreviewThemeAccent: (ThemeAccent) -> Unit = {},
    onFinishOnboarding: (displayName: String, photoPath: String?, profileAnswers: OnboardingProfileAnswers?) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var nameInput by remember { mutableStateOf("") }
    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }
    var photoVersion by remember { mutableIntStateOf(0) }
    var isNameFocused by remember { mutableStateOf(false) }

    // Onboarding wizard step: 0 = Name & Photo, 1..6 = Questions, 7 = Summary
    var wizardStep by remember { mutableIntStateOf(0) }
    var quizAnswers by remember(initialAnswers) { mutableStateOf(initialAnswers) }
    var quizMascotBubble by remember { mutableStateOf<String?>(null) }

    // Citra Mascot choreography states
    var humor by remember { mutableStateOf(MascotHumor.SURPRESO) }
    var olharX by remember { mutableStateOf(0f) }
    var olharY by remember { mutableStateOf(0f) }
    var nodTrigger by remember { mutableIntStateOf(0) }
    var jumpTrigger by remember { mutableIntStateOf(0) }

    var showHeaderText by remember { mutableStateOf(false) }
    var showProfileCard by remember { mutableStateOf(false) }

    val mascotBubbles = remember {
        listOf(
            "Olá! Eu sou o Citra 🍊",
            "Pronto para focar juntos!",
            "Um passo de cada vez ✨",
            "Seu espaço é 100% seu!"
        )
    }
    var bubbleIndex by remember { mutableIntStateOf(0) }

    // Entrance spring animation for the mascot
    val entranceScale = remember { Animatable(0.25f) }
    val entranceAlpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "citra_welcome_idle")
    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mascot_float"
    )
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )

    // Initial startup animation sequence
    LaunchedEffect(Unit) {
        launch {
            entranceAlpha.animateTo(1f, animationSpec = tween(320))
        }
        launch {
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        delay(220)
        olharX = -0.65f
        delay(260)
        olharX = 0.65f
        delay(260)
        olharX = 0f
        humor = MascotHumor.FELIZ
        jumpTrigger++
        showHeaderText = true
        delay(240)
        showProfileCard = true
    }

    // Mascot gaze reacts when user focuses or types their name
    LaunchedEffect(isNameFocused, nameInput) {
        if (isNameFocused) {
            humor = MascotHumor.NEUTRO
            olharY = 0.7f
            olharX = ((nameInput.length % 10) / 10f - 0.5f) * 0.7f
        } else if (showHeaderText) {
            olharX = 0f
            olharY = 0f
            humor = MascotHumor.FELIZ
        }
    }

    // Photo Picker (Gallery) - zero storage permissions needed
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    try {
                        val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
                        avatarDir.listFiles()?.forEach { it.delete() }
                        val targetFile = File(avatarDir, "user_profile_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        targetFile.absolutePath
                    } catch (_: Exception) {
                        null
                    }
                }
                if (savedPath != null) {
                    selectedPhotoPath = savedPath
                    photoVersion++
                    humor = MascotHumor.FELIZ
                    nodTrigger++
                } else {
                    Toast.makeText(context, "Não foi possível salvar a foto.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Camera capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            coroutineScope.launch {
                val savedPath = withContext(Dispatchers.IO) {
                    try {
                        val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
                        avatarDir.listFiles()?.forEach { it.delete() }
                        val targetFile = File(avatarDir, "user_profile_${System.currentTimeMillis()}.jpg")
                        targetFile.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        targetFile.absolutePath
                    } catch (_: Exception) {
                        null
                    }
                }
                if (savedPath != null) {
                    selectedPhotoPath = savedPath
                    photoVersion++
                    humor = MascotHumor.FELIZ
                    nodTrigger++
                } else {
                    Toast.makeText(context, "Não foi possível salvar a foto da câmera.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(CitraHeroTop, CitraHeroMid, CitraHeroBottom)
                )
            )
            .drawBehind {
                // Soft ambient citrus glow circles behind the mascot
                drawCircle(
                    color = Color.White.copy(alpha = 0.12f),
                    radius = size.minDimension * 0.42f * haloPulse,
                    center = Offset(size.width * 0.5f, size.height * 0.22f)
                )
                drawCircle(
                    color = Color(0xFFFFD180).copy(alpha = 0.16f),
                    radius = size.minDimension * 0.28f,
                    center = Offset(size.width * 0.14f, size.height * 0.12f)
                )
                drawCircle(
                    color = Color(0xFFFFAB40).copy(alpha = 0.18f),
                    radius = size.minDimension * 0.32f,
                    center = Offset(size.width * 0.88f, size.height * 0.88f)
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Speech bubble pill above mascot
                AnimatedVisibility(
                    visible = showHeaderText,
                    enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { -20 }
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (wizardStep == 0) {
                                mascotBubbles[bubbleIndex]
                            } else {
                                quizMascotBubble ?: "Personalizando o Citra para você 🍊"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (wizardStep == 0) 14.dp else 8.dp))

                // Interactive Animated Citra Mascot
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = entranceScale.value
                            scaleY = entranceScale.value
                            alpha = entranceAlpha.value
                            translationY = floatOffsetY.dp.toPx()
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            humor = MascotHumor.FELIZ
                            jumpTrigger++
                            nodTrigger++
                            bubbleIndex = (bubbleIndex + 1) % mascotBubbles.size
                        }
                        .testTag("welcome_mascot_hero")
                ) {
                    CitraMascote(
                        humor = humor,
                        olharX = olharX,
                        olharY = olharY,
                        tamanho = if (wizardStep == 0) 128.dp else 86.dp,
                        folha = true,
                        piscar = true,
                        nodTrigger = nodTrigger,
                        jumpTrigger = jumpTrigger
                    )
                }

                Spacer(modifier = Modifier.height(if (wizardStep == 0) 12.dp else 8.dp))

                // Title & subtitle (compact when inside questionnaire steps)
                AnimatedVisibility(
                    visible = showHeaderText && wizardStep == 0,
                    enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { 24 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Bem-vindo ao Citra",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Personalize seu perfil local para começar sua rotina de foco, hábitos e anotações.",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color.White.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (wizardStep == 0) 22.dp else 10.dp))

                // Step 0 (Name + Photo) OR Steps 1..7 (Adaptive Questionnaire + Summary)
                AnimatedVisibility(
                    visible = showProfileCard,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(320)) { 48 }
                ) {
                    if (wizardStep == 0) {
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 10.dp,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 22.dp, vertical = 22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Profile Photo Preview
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shadowElevation = 3.dp,
                                        modifier = Modifier
                                            .size(92.dp)
                                            .clip(CircleShape)
                                            .border(
                                                2.5.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                                CircleShape
                                            )
                                            .clickable {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                            .testTag("welcome_avatar_button")
                                    ) {
                                        if (!selectedPhotoPath.isNullOrBlank()) {
                                            AsyncImage(
                                                model = File(selectedPhotoPath!!),
                                                contentDescription = "Foto de perfil selecionada",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            val initial = nameInput.trim().take(1).uppercase()
                                            Box(contentAlignment = Alignment.Center) {
                                                if (initial.isNotEmpty()) {
                                                    Text(
                                                        text = initial,
                                                        fontSize = 36.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = "Escolher foto de perfil",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(40.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        shadowElevation = 3.dp,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Photo source buttons (Gallery / Camera / Clear)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("welcome_gallery_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Galeria", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    OutlinedButton(
                                        onClick = { cameraLauncher.launch(null) },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("welcome_camera_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Câmera", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    if (!selectedPhotoPath.isNullOrBlank()) {
                                        IconButton(
                                            onClick = {
                                                selectedPhotoPath = null
                                                nodTrigger++
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("welcome_remove_photo_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remover foto selecionada",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Display Name Field
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { if (it.length <= 40) nameInput = it },
                                    label = { Text("Como gostaria de ser chamado?") },
                                    placeholder = { Text("Viajante Citra") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingIcon = {
                                        if (nameInput.isNotEmpty()) {
                                            IconButton(onClick = { nameInput = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Limpar nome"
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            focusManager.clearFocus()
                                            wizardStep = 1
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isNameFocused = it.isFocused }
                                        .testTag("welcome_name_input")
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                // Primary CTA advances into the short 6-step personalization flow
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        wizardStep = 1
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .testTag("welcome_continue_button")
                                ) {
                                    Text(
                                        text = "Continuar",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                TextButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        onFinishOnboarding(nameInput.trim(), selectedPhotoPath, null)
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("welcome_skip_button")
                                ) {
                                    Text(
                                        text = "Usar padrão agora",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "Seu nome e foto ficam salvos apenas neste aparelho.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        ProfileQuestionnaireCard(
                            displayName = nameInput.trim(),
                            answers = quizAnswers,
                            currentStep = wizardStep,
                            onAnswersChange = { updated ->
                                quizAnswers = updated
                                // Live preview theme brightness & accent when adjusting Step 6
                                val nextThemeMode: Boolean? = when (updated.themeBrightness) {
                                    ThemeBrightnessChoice.LIGHT -> false
                                    ThemeBrightnessChoice.DARK -> true
                                    ThemeBrightnessChoice.SYSTEM -> null
                                }
                                onPreviewThemeMode(nextThemeMode)
                                val previewAccent = when (updated.visualMood) {
                                    VisualMoodChoice.TANGERINA -> ThemeAccent.TANGERINA
                                    VisualMoodChoice.CALMA -> ThemeAccent.VERDE_MENTA
                                    VisualMoodChoice.FOCO -> ThemeAccent.AZUL_OCEANO
                                    VisualMoodChoice.CRIATIVO -> ThemeAccent.ROXO_COSMICO
                                    VisualMoodChoice.SURPRISE_DAILY -> ThemeAccent.TANGERINA
                                }
                                onPreviewThemeAccent(previewAccent)
                            },
                            onStepChange = { nextStep -> wizardStep = nextStep },
                            onBackFromFirstStep = {
                                quizMascotBubble = null
                                humor = MascotHumor.FELIZ
                                wizardStep = 0
                            },
                            onSkipToDefault = {
                                onPreviewThemeMode(null)
                                onPreviewThemeAccent(ThemeAccent.TANGERINA)
                                onFinishOnboarding(nameInput.trim(), selectedPhotoPath, null)
                            },
                            onFinishQuiz = { finalAnswers ->
                                onFinishOnboarding(nameInput.trim(), selectedPhotoPath, finalAnswers)
                            },
                            onMascotReaction = { feedback ->
                                quizMascotBubble = feedback.bubbleText
                                humor = feedback.humor
                                if (feedback.jump) jumpTrigger++ else nodTrigger++
                            },
                            finishButtonLabel = "Entrar no meu Citra",
                            showSkipButton = true
                        )
                    }
                }
            }
        }
    }
}
