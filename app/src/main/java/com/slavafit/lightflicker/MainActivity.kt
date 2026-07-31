package com.slavafit.lightflicker

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.slavafit.lightflicker.data.AppSettings
import com.slavafit.lightflicker.data.ThemeMode
import com.slavafit.lightflicker.measurement.Confidence
import com.slavafit.lightflicker.measurement.LumaAnalyzer
import com.slavafit.lightflicker.measurement.MeasurementResult
import com.slavafit.lightflicker.measurement.ResultZone
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(viewModel, this) }
    }
}

private enum class Screen { ONBOARDING, PERMISSION, HOME, GUIDE, CAMERA, RESULT, SETTINGS, UNDERSTAND }

@Composable
private fun App(viewModel: AppViewModel, activity: ComponentActivity) {
    val settings by viewModel.settings.collectAsState()
    val value = settings ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
    val systemDark = isSystemInDarkTheme()
    val dark = when (value.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val baseContext = LocalContext.current
    val localeConfig = remember(value.language, baseContext) {
        android.content.res.Configuration(baseContext.resources.configuration).also {
            if (value.language.isNotBlank()) it.setLocale(Locale.forLanguageTag(value.language))
        }
    }
    val localeContext = remember(localeConfig, baseContext) { baseContext.createConfigurationContext(localeConfig) }
    CompositionLocalProvider(
        LocalContext provides localeContext,
        LocalConfiguration provides localeConfig,
        LocalActivityResultRegistryOwner provides activity,
    ) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            AppNavigation(viewModel, value, activity)
        }
    }
}

@Composable
private fun AppNavigation(viewModel: AppViewModel, settings: AppSettings, activity: ComponentActivity) {
    val hasCamera = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var screen by remember(settings.onboardingDone) {
        mutableStateOf(if (settings.onboardingDone) if (hasCamera) Screen.HOME else Screen.PERMISSION else Screen.ONBOARDING)
    }
    val result by viewModel.result.collectAsState()
    LaunchedEffect(result) { if (result != null && screen == Screen.CAMERA) screen = Screen.RESULT }

    when (screen) {
        Screen.ONBOARDING -> Onboarding {
            viewModel.completeOnboarding()
            screen = Screen.PERMISSION
        }
        Screen.PERMISSION -> PermissionScreen(onGranted = { screen = Screen.HOME })
        Screen.HOME -> HomeScreen(
            onMeasure = { viewModel.resetMeasurement(); screen = Screen.CAMERA },
            onGuide = { screen = Screen.GUIDE },
            onSettings = { screen = Screen.SETTINGS },
        )
        Screen.GUIDE -> TextPage(R.string.instruction_title, R.string.instruction_steps) { screen = Screen.HOME }
        Screen.CAMERA -> CameraScreen(viewModel, onBack = { viewModel.resetMeasurement(); screen = Screen.HOME })
        Screen.RESULT -> ResultScreen(
            result = result,
            onRepeat = { viewModel.resetMeasurement(); screen = Screen.CAMERA },
            onUnderstand = { screen = Screen.UNDERSTAND },
            onHome = { viewModel.resetMeasurement(); screen = Screen.HOME },
        )
        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            viewModel = viewModel,
            onGuide = { screen = Screen.GUIDE },
            onBack = { screen = Screen.HOME },
        )
        Screen.UNDERSTAND -> TextPage(R.string.understand_result, R.string.understand_body) { screen = Screen.RESULT }
    }
}

@Composable
private fun Onboarding(onContinue: () -> Unit) = CenteredPage {
    Logo()
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.onboarding_body), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(32.dp))
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.continue_action)) }
}

@Composable
private fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted() else denied = true
    }
    CenteredPage {
        Text(stringResource(R.string.camera_permission_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.camera_permission_body), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.allow_camera))
        }
        if (denied) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.open_settings)) }
        }
    }
}

@Composable
private fun HomeScreen(onMeasure: () -> Unit, onGuide: () -> Unit, onSettings: () -> Unit) = CenteredPage {
    Logo()
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.home_body), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(28.dp))
    Button(onClick = onMeasure, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.start_measurement)) }
    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = onGuide, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.instructions)) }
    TextButton(onClick = onSettings, modifier = Modifier.height(52.dp)) { Text(stringResource(R.string.settings)) }
}

@Composable
private fun CameraScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sample by viewModel.latestSample.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val measuring by viewModel.measuring.collectAsState()
    var cameraError by remember { mutableStateOf(false) }
    var autoStartTriggered by remember { mutableStateOf(false) }
    val analyzer = remember { LumaAnalyzer(viewModel::onFrame) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(Unit) {
        delay(1_000)
        while (!autoStartTriggered) {
            val current = viewModel.latestSample.value
            val ready = current != null &&
                current.brightness in 30.0..245.0 &&
                current.saturatedRatio < 0.18 &&
                current.motion <= 22.0
            if (!cameraError && ready) {
                autoStartTriggered = true
                viewModel.startMeasurement()
            } else {
                delay(200)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose { executor.shutdown() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PreviewView(it).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    runCatching {
                        val provider = future.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                            .build().also { it.setAnalyzer(executor, analyzer) }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }.onFailure { cameraError = true }
                }, ContextCompat.getMainExecutor(context))
            },
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .height(230.dp)
                .border(3.dp, if (measuring) Color(0xFFFFC107) else Color.White, RoundedCornerShape(20.dp))
        )
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    cameraError -> stringResource(R.string.camera_error)
                    measuring -> stringResource(R.string.hold_still)
                    sample == null -> stringResource(R.string.camera_hint)
                    sample!!.brightness < 30 -> stringResource(R.string.too_dark)
                    sample!!.saturatedRatio > 0.18 -> stringResource(R.string.too_bright)
                    sample!!.motion > 22 -> stringResource(R.string.movement)
                    else -> stringResource(R.string.ready)
                },
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (measuring) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.measuring, (progress * 100).toInt()), color = Color.White)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.6f)).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!measuring) {
                Text(stringResource(R.string.automatic_start), color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.back), color = Color.White)
            }
        }
    }
}

@Composable
private fun ResultScreen(
    result: MeasurementResult?,
    onRepeat: () -> Unit,
    onUnderstand: () -> Unit,
    onHome: () -> Unit,
) {
    if (result == null) return CenteredPage { CircularProgressIndicator() }
    val color = zoneColor(result.zone)
    val status = when (result.zone) {
        ResultZone.GREEN -> R.string.green_status
        ResultZone.YELLOW -> R.string.yellow_status
        ResultZone.RED -> R.string.red_status
        ResultZone.GRAY -> R.string.gray_status
    }
    val message = when (result.zone) {
        ResultZone.GREEN -> R.string.green_message
        ResultZone.YELLOW -> R.string.yellow_message
        ResultZone.RED -> R.string.red_message
        ResultZone.GRAY -> R.string.gray_message
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.result_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(stringResource(R.string.frequency), result.frequencyHz?.let { "%.1f Hz".format(it) } ?: stringResource(R.string.unknown_value), Modifier.weight(1f))
            MetricCard(stringResource(R.string.modulation), result.flickerPercent?.let { "%.1f %%".format(it) } ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        ResultScale(result.zone)
        Spacer(Modifier.height(16.dp))
        Text("●  ${stringResource(status)}", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(message), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Text("${stringResource(R.string.confidence)}: ${confidenceText(result.confidence)}")
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRepeat, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.repeat)) }
        TextButton(onClick = onUnderstand, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.understand_result)) }
        TextButton(onClick = onHome, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.close)) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ResultScale(zone: ResultZone) {
    val position = when (zone) {
        ResultZone.GREEN -> 0.17f
        ResultZone.YELLOW -> 0.5f
        ResultZone.RED -> 0.83f
        ResultZone.GRAY -> 0.5f
    }
    Canvas(Modifier.fillMaxWidth().height(54.dp)) {
        val top = 22.dp.toPx()
        val h = 16.dp.toPx()
        val third = size.width / 3
        drawRoundRect(Color(0xFF2E7D32), Offset(0f, top), Size(third, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
        drawRect(Color(0xFFF9A825), Offset(third, top), Size(third, h))
        drawRoundRect(Color(0xFFC62828), Offset(third * 2, top), Size(third, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
        val x = size.width * position
        val marker = if (zone == ResultZone.GRAY) Color.Gray else Color.White
        drawLine(marker, Offset(x, 5.dp.toPx()), Offset(x, top + h + 8.dp.toPx()), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(marker, 5.dp.toPx(), Offset(x, 5.dp.toPx()), style = Stroke(3.dp.toPx()))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: AppSettings,
    viewModel: AppViewModel,
    onGuide: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var linkError by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Choice("", R.string.system_language, settings.language.isBlank(), viewModel::setLanguage)
            Choice("en", R.string.english, settings.language == "en", viewModel::setLanguage)
            Choice("ru", R.string.russian, settings.language == "ru", viewModel::setLanguage)
            Choice("es", R.string.spanish, settings.language == "es", viewModel::setLanguage)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ThemeChoice(ThemeMode.SYSTEM, R.string.system_theme, settings.theme, viewModel::setTheme)
            ThemeChoice(ThemeMode.LIGHT, R.string.light_theme, settings.theme, viewModel::setTheme)
            ThemeChoice(ThemeMode.DARK, R.string.dark_theme, settings.theme, viewModel::setTheme)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onGuide, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.instructions)) }

            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.about), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Logo()
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.about_purpose))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.developer), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Slavafit")))
                } catch (_: ActivityNotFoundException) {
                    linkError = true
                }
            }, modifier = Modifier.height(48.dp)) { Text(stringResource(R.string.telegram)) }
            if (linkError) Text(stringResource(R.string.link_error), color = MaterialTheme.colorScheme.error)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(stringResource(R.string.disclaimer), Modifier.padding(16.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Choice(value: String, label: Int, selected: Boolean, action: (String) -> Unit) =
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = { action(value) })
        TextButton(onClick = { action(value) }) { Text(stringResource(label)) }
    }

@Composable
private fun ThemeChoice(value: ThemeMode, label: Int, selected: ThemeMode, action: (ThemeMode) -> Unit) =
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected == value, onClick = { action(value) })
        TextButton(onClick = { action(value) }) { Text(stringResource(label)) }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextPage(title: Int, body: Int, onBack: () -> Unit) =
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(title)) }, navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } }) }) { padding ->
        Text(stringResource(body), Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), style = MaterialTheme.typography.bodyLarge)
    }

@Composable
private fun CenteredPage(content: @Composable ColumnScope.() -> Unit) =
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )

@Composable
private fun Logo() = Box(
    Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center,
) { Text("≈", fontSize = 54.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }

@Composable
private fun confidenceText(value: Confidence) = stringResource(
    when (value) {
        Confidence.HIGH -> R.string.high
        Confidence.MEDIUM -> R.string.medium
        Confidence.LOW -> R.string.low
    }
)

private fun zoneColor(zone: ResultZone) = when (zone) {
    ResultZone.GREEN -> Color(0xFF2E7D32)
    ResultZone.YELLOW -> Color(0xFFF9A825)
    ResultZone.RED -> Color(0xFFC62828)
    ResultZone.GRAY -> Color.Gray
}
