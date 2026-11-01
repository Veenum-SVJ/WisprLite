package com.edib.openwhispr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.tabs.TabLayout
import java.io.File

class MainActivity : AppCompatActivity() {
    private var navigation: WisprNavigation? = null
    private var settingsExtras: SettingsExtras? = null

    private lateinit var statusSubtitle: TextView
    // [ui] Status row dot + tap-to-fix target, and the cloud switch's subtitle.
    private lateinit var statusRow: LinearLayout
    private lateinit var statusDotView: View
    private var statusAction: (() -> Unit)? = null
    private lateinit var cloudRowSub: TextView
    private lateinit var audioRow: LinearLayout
    private lateinit var audioRowSub: TextView
    private lateinit var audioDot: View
    private lateinit var accRow: LinearLayout
    private lateinit var accRowSub: TextView
    private lateinit var accDot: View
    private lateinit var accCaption: TextView
    private lateinit var batteryRow: LinearLayout
    private lateinit var batteryRowSub: TextView
    private lateinit var batteryDot: View
    private lateinit var setupCollapsedRow: LinearLayout
    private lateinit var setupCollapsedRowSub: TextView
    private lateinit var setupDoneSummary: TextView
    private lateinit var keyRowSub: TextView
    private lateinit var cleanupProviderRowSub: TextView
    private lateinit var customInstructionsRowSub: TextView
    private lateinit var customInstructionsRow: LinearLayout
    private lateinit var cleanupProviderRow: LinearLayout
    private lateinit var languageRowSub: TextView
    private lateinit var languageRow: LinearLayout
    private lateinit var modelContainer: LinearLayout
    private lateinit var tabLayout: TabLayout
    private lateinit var statusContainer: LinearLayout
    private lateinit var dictationContainer: LinearLayout
    private lateinit var settingsContainer: LinearLayout

    private val modelRows = mutableMapOf<String, ModelRowViews>()
    private var batteryWarningShown = false
    private var setupExpanded = false

    private data class ModelRowViews(
        val radio: MaterialRadioButton,
        val progress: LinearProgressIndicator,
        val subtitle: TextView,
        val dlBtn: MaterialButton,
        val delBtn: MaterialButton,
    )

    // Backup flows: the passphrase and the snapshot live only while a dialog runs.
    private var backupPassphrase: String? = null
    private var pendingExport: SettingsBackup.Snapshot? = null
    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) writeBackup(uri)
        else { backupPassphrase = null; pendingExport = null }
    }
    private val openBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) readBackup(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(prefs().getInt("appearance", -1))
        super.onCreate(savedInstanceState)
        ServiceWatchdog.schedule(this)
        RetentionWorker.schedule(this)

        // First launch on a fresh install (no saved settings yet): offer to restore a backup.
        if (!prefs().contains("welcome_done") && prefs().all.isEmpty()) {
            window.decorView.post { showWelcome() }
        }

        // Keep the native WisprLite splash brief; the static brand mark needs no animation delay.
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            splashScreen.setOnExitAnimationListener { splash ->
                val start = splash.iconAnimationStart?.toEpochMilli()
                val duration = splash.iconAnimationDuration?.toMillis() ?: 0L
                val remaining = if (start == null) 0L
                    else (start + duration - System.currentTimeMillis()).coerceIn(0L, 1000L)
                splash.animate().alpha(0f).setStartDelay(remaining).setDuration(200)
                    .withEndAction { splash.remove() }.start()
            }
        }

        // Best-effort: lets the background service show its "still running"
        // notification (Android 13+ requires this permission for any
        // notification, including the foreground-service one). Not gated on
        // anything -- dictation works fine without it, this just makes the
        // service more likely to survive being swiped from Recents.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            !hasPerm(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }

        // [privacy] The automatic update check on app open is opt-in (off by
        // default, including for existing installs), so the app makes no
        // request to GitHub unless the user enables it or taps "Check for
        // updates".
        // WisprLite updates are distributed through GitHub releases, never installed in-app.

        val outer = vertical(0, 0)

        // Top large header (like "Connected devices"), with the app icon
        // alongside the name.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // [ui] Less empty space above the title (was 64dp top).
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) }
        })
        header.addView(TextView(this).apply {
            text = "WisprLite"
            textSize = 32f
        })
        outer.addView(header)

        tabLayout = TabLayout(this).apply {
            addTab(newTab().setText(getString(R.string.tab_status)))
            addTab(newTab().setText(getString(R.string.tab_dictation)))
            addTab(newTab().setText(getString(R.string.tab_settings)))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) { showTab(tab.position) }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }
        outer.addView(tabLayout)

        // [ui] The status tab no longer starts with a section header, so
        // give it the same breathing room below the tabs.
        statusContainer = vertical(0).apply { setPadding(0, dp(16), 0, 0) }
        dictationContainer = vertical(0)
        settingsContainer = vertical(0)

        // [ui] Each section's rows sit on a rounded panel (see group()).
        // Rows, handlers and the references refresh() uses are unchanged;
        // only the parent they're added to is.

        // ================= Status tab =================

        statusDotView = statusDot()
        statusRow = settingsRow(getString(R.string.tab_status), getString(R.string.checking), leading = statusDotView) {
            statusAction?.invoke()
        }
        statusSubtitle = statusRow.findViewWithTag("subtitle")
        statusContainer.addView(group().apply { addView(statusRow) })

        // --- Setup checklist card ---
        statusContainer.addView(sectionHeader(getString(R.string.setup)))
        val setupGroup = group()
        setupCollapsedRow = settingsRow(getString(R.string.setup), getString(R.string.checking)) {
            setupExpanded = !setupExpanded
            refresh()
        }
        setupCollapsedRowSub = setupCollapsedRow.findViewWithTag("subtitle")
        setupGroup.addView(setupCollapsedRow)

        setupDoneSummary = TextView(this).apply {
            textSize = 14f
            setTextColor(DOT_GREEN)
            setPadding(dp(24), 0, dp(24), dp(8))
        }
        setupGroup.addView(setupDoneSummary)

        audioDot = statusDot()
        audioRow = settingsRow(getString(R.string.audio_permission), getString(R.string.checking), leading = audioDot) {
            if (!hasPerm(Manifest.permission.RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
        }
        audioRowSub = audioRow.findViewWithTag("subtitle")
        setupGroup.addView(audioRow)

        accDot = statusDot()
        accRow = settingsRow(getString(R.string.accessibility_service), getString(R.string.checking), leading = accDot) {
            val alreadyEnabled = WhisperAccessibilityService.instance != null
            // The restricted-settings help is for a toggle that can't be switched on yet;
            // an enabled-but-stopped service just needs the toggle cycled.
            if (!alreadyEnabled && android.os.Build.VERSION.SDK_INT >= 33 &&
                !ServiceWatchdog.isEnabledInSettings(this)) {
                showRestrictedSettingsHelp()
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        accRowSub = accRow.findViewWithTag("subtitle")
        setupGroup.addView(accRow)

        accCaption = TextView(this).apply {
            text = getString(R.string.accessibility_help)
            textSize = 12f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            alpha = 0.8f
            setPadding(dp(24), 0, dp(24), dp(12))
        }
        setupGroup.addView(accCaption)

        batteryDot = statusDot()
        batteryRow = settingsRow(getString(R.string.battery_optimization), getString(R.string.checking), leading = batteryDot) {
            requestBatteryExemption()
        }
        batteryRowSub = batteryRow.findViewWithTag("subtitle")
        setupGroup.addView(batteryRow)
        statusContainer.addView(setupGroup)

        // --- Background service ---
        statusContainer.addView(sectionHeader("Service")) // [ui]
        val serviceEnabled = prefs().getBoolean("service_master_enabled", true)
        val serviceSwitch = MaterialSwitch(this).apply {
            isChecked = serviceEnabled
            isClickable = false
        }
        val serviceRow = settingsRow(
            getString(R.string.background_service),
            getString(R.string.background_service_summary),
            serviceSwitch
        ) {
            val newVal = !serviceSwitch.isChecked
            prefs().edit().putBoolean("service_master_enabled", newVal).apply()
            serviceSwitch.isChecked = newVal
            if (!newVal) ServiceWatchdog.clearWarning(this)
            WhisperAccessibilityService.instance?.refreshMasterEnabled()
        }
        statusContainer.addView(group().apply { addView(serviceRow) })

        // ================= Dictation tab =================

        dictationContainer.addView(sectionHeader(getString(R.string.engine)))

        val isCloud = !prefs().getBoolean("use_local", true)
        val cloudSwitch = MaterialSwitch(this).apply {
            isChecked = isCloud
            isClickable = false
        }
        // [ui] Subtitle is set from the switch state in refresh().
        val cloudRow = settingsRow(getString(R.string.cloud_transcription), "", cloudSwitch) {
            val newCloud = !cloudSwitch.isChecked
            prefs().edit().putBoolean("use_local", !newCloud).apply()
            cloudSwitch.isChecked = newCloud
            WhisperAccessibilityService.instance?.reloadModel()
            refresh()
        }
        cloudRowSub = cloudRow.findViewWithTag("subtitle")
        dictationContainer.addView(group().apply { addView(cloudRow) })

        modelContainer = vertical(0)
        val modelGroup = group()
        for (m in MODEL_CATALOG) modelGroup.addView(buildModelRow(m))
        modelContainer.addView(modelGroup)
        dictationContainer.addView(modelContainer)

        languageRow = settingsRow(getString(R.string.transcription_languages), "") {
            showLanguageProfilePicker()
        }
        languageRowSub = languageRow.findViewWithTag("subtitle")
        // Language selection lives in the WisprLite settings controls.

        dictationContainer.addView(sectionHeader(getString(R.string.text_enhancement)))
        val postGroup = group()

        val isPostProcessing = prefs().getBoolean("use_post_processing", false)
        val postProcessSwitch = MaterialSwitch(this).apply {
            isChecked = isPostProcessing
            isClickable = false
        }
        val postProcessRow = settingsRow(getString(R.string.cleanup_transcript), getString(R.string.text_enhancement_help), postProcessSwitch) {
            val newVal = !postProcessSwitch.isChecked
            prefs().edit().putBoolean("use_post_processing", newVal).apply()
            postProcessSwitch.isChecked = newVal
            refresh()
        }
        postGroup.addView(postProcessRow)

        cleanupProviderRow = settingsRow(getString(R.string.cleanup_service), getString(R.string.cleanup_service_groq_ready)) {
            promptCleanupProvider()
        }
        cleanupProviderRowSub = cleanupProviderRow.findViewWithTag("subtitle")
        dictationContainer.addView(cleanupProviderRow)

        customInstructionsRow = settingsRow("Custom refinements", getString(R.string.tap_add_refinements)) {
            promptCustomInstructions()
        }
        customInstructionsRowSub = customInstructionsRow.findViewWithTag("subtitle")
        customInstructionsRowSub.maxLines = 2
        customInstructionsRowSub.ellipsize = android.text.TextUtils.TruncateAt.END
        postGroup.addView(customInstructionsRow)
        dictationContainer.addView(postGroup)

        // ================= Settings tab =================

        val keyRow = settingsRow("Groq cleanup API key", "Optional Groq key override") { promptApiKey() }
        keyRowSub = keyRow.findViewWithTag("subtitle")
        settingsContainer.addView(group().apply { addView(keyRow) })
        settingsContainer.addView(sectionHeader(getString(R.string.bubble_size_section)))
        val bubbleSizePanel = vertical(dp(24), dp(8))
        val bubbleSizeLabel = TextView(this).apply {
            text = getString(R.string.bubble_size)
            textSize = 18f
            setTextColor(attrColor(android.R.attr.textColorPrimary))
        }
        bubbleSizePanel.addView(bubbleSizeLabel)

        val selectedBubbleSize = TextView(this).apply {
            textSize = 14f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(2), 0, dp(4))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        bubbleSizePanel.addView(selectedBubbleSize)

        val bubblePreview = ImageView(this).apply {
            tag = "bubble-size-preview"
            setImageResource(R.drawable.ic_app_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ContextCompat.getColor(this@MainActivity, R.color.brand_blue)) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        fun updateBubblePreview(percent: Int) {
            val size = dp(BubbleSize.dimensions(percent).buttonDp)
            bubblePreview.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
        }
        val previewStage = FrameLayout(this).apply {
            background = ModernUi.surface(this@MainActivity, muted = true)
            addView(bubblePreview)
        }
        bubbleSizePanel.addView(previewStage, LinearLayout.LayoutParams(LP_MATCH, dp(104)).apply { topMargin = dp(8); bottomMargin = dp(8) })
        updateBubblePreview(currentBubbleSizePercent())

        bubbleSizeLabel.id = View.generateViewId()
        val bubbleSizeSlider = Slider(this).apply {
            id = View.generateViewId()
            valueFrom = BubbleSize.MIN_PERCENT.toFloat()
            valueTo = BubbleSize.MAX_PERCENT.toFloat()
            stepSize = BubbleSize.STEP_PERCENT.toFloat()
            value = currentBubbleSizePercent().toFloat()
            setLabelFormatter { localizedBubbleLabel(it.toInt()) }
            contentDescription = getString(R.string.bubble_size_accessibility)
            layoutParams = LinearLayout.LayoutParams(LP_MATCH, dp(64))
        }
        bubbleSizeLabel.labelFor = bubbleSizeSlider.id
        bubbleSizeSlider.addOnChangeListener { slider, value, fromUser ->
            val percent = BubbleSize.sliderPercent(value.toInt())
            updateBubblePreview(percent)
            selectedBubbleSize.text = localizedBubbleLabel(percent)
            slider.contentDescription = "${getString(R.string.bubble_size_accessibility)}, ${localizedBubbleDescription(percent)}"
            if (fromUser) {
                prefs().edit().putInt(BubbleSize.PREFERENCE_KEY, percent).apply()
                WhisperAccessibilityService.instance?.refreshBubbleSize()
            }
        }
        val initialBubbleSize = currentBubbleSizePercent()
        selectedBubbleSize.text = localizedBubbleLabel(initialBubbleSize)
        bubbleSizeSlider.contentDescription = "${getString(R.string.bubble_size_accessibility)}, ${localizedBubbleDescription(initialBubbleSize)}"
        bubbleSizePanel.addView(bubbleSizeSlider)

        val rangeCaption = TextView(this).apply {
            text = getString(
                R.string.bubble_size_help,
                BubbleSize.MIN_PERCENT,
                BubbleSize.MAX_PERCENT,
                BubbleSize.DEFAULT_PERCENT,
            )
            textSize = 12f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            labelFor = bubbleSizeSlider.id
        }
        bubbleSizePanel.addView(rangeCaption)
        settingsContainer.addView(bubbleSizePanel)

        settingsContainer.addView(sectionHeader(getString(R.string.backup_section)))
        val backupGroup = group()
        backupGroup.addView(settingsRow(getString(R.string.backup_export_title), getString(R.string.backup_export_summary)) {
            exportBackup()
        })
        backupGroup.addView(settingsRow(getString(R.string.backup_restore_title), getString(R.string.backup_restore_summary)) {
            openBackupLauncher.launch(arrayOf("*/*"))
        })
        settingsContainer.addView(backupGroup)

        settingsContainer.addView(sectionHeader(getString(R.string.about)))
        val aboutGroup = group()
        aboutGroup.addView(settingsRow(getString(R.string.language), getString(R.string.language_summary)) {
            // Native names for the new locales; "" follows the system.
            val options = listOf(
                getString(R.string.system_default) to "",
                getString(R.string.english) to "en",
                getString(R.string.french) to "fr",
                "Italiano" to "it",
                "Español" to "es",
                "Русский" to "ru",
                "日本語" to "ja",
                "Português (Brasil)" to "pt-BR",
                "中文 (简体)" to "zh-CN",
                "Türkçe" to "tr",
            )
            val appTag = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
            val selected = options.indexOfFirst { (_, tag) ->
                if (tag.isEmpty()) appTag == null
                else appTag == tag || appTag?.substringBefore('-') == tag.substringBefore('-')
            }.coerceAtLeast(0)
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setSingleChoiceItems(options.map { it.first }.toTypedArray(), selected) { dialog, which ->
                    val tag = options[which].second
                    val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                        else LocaleListCompat.forLanguageTags(tag)
                    AppCompatDelegate.setApplicationLocales(locales)
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        })

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: getString(R.string.unknown_version)
        } catch (e: Exception) {
            getString(R.string.unknown_version)
        }
        // [ui] The separate "Version" row is folded into "Check for updates" below.

        aboutGroup.addView(settingsRow(getString(R.string.github), getString(R.string.github_summary)) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Veenum-SVJ/WisprLite")))
            } catch (e: Exception) {
                toast(getString(R.string.open_browser_failed, e.message ?: getString(R.string.unknown_error)))
            }
        })

        settingsContainer.addView(aboutGroup)

        outer.addView(statusContainer)
        outer.addView(dictationContainer)
        outer.addView(settingsContainer)
        showTab(0)

        val extras = SettingsExtras(this, savedInstanceState)
        settingsExtras = extras
        fun detach(view: View): View {
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            return view
        }
        val cleanupControls = vertical(0).apply {
            addView(detach(cleanupProviderRow))
            addView(detach(postGroup))
            addView(detach(keyRow))
        }
        detach(modelContainer)
        // Keep recognition and cleanup controls live while giving each one a single settings home.
        while (dictationContainer.childCount > 2) dictationContainer.removeViewAt(2)
        val interfaceControls = detach(aboutGroup.getChildAt(0))
        statusContainer.visibility = View.VISIBLE
        dictationContainer.visibility = View.VISIBLE
        val allSettings = extras.build(detach(statusContainer), detach(dictationContainer), modelContainer, cleanupControls,
            detach(bubbleSizePanel), detach(backupGroup), interfaceControls, detach(aboutGroup))
        navigation = WisprNavigation(this, allSettings, savedInstanceState) { extras.handleBack() }
        setContentView(navigation!!.build())

        if (!hasPerm(Manifest.permission.RECORD_AUDIO)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        refresh()
    }

    override fun onResume() { super.onResume(); refresh(); navigation?.refresh() }
    override fun onDestroy() {
        backupPassphrase = null
        pendingExport = null
        navigation?.close()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) { navigation?.saveState(outState); settingsExtras?.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onRequestPermissionsResult(c: Int, p: Array<String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r); refresh()
    }

    private fun showTab(index: Int) {
        if (navigation != null) {
            statusContainer.visibility = View.VISIBLE
            dictationContainer.visibility = View.VISIBLE
            settingsContainer.visibility = View.VISIBLE
            navigation?.openSettings()
            return
        }
        statusContainer.visibility = if (index == 0) View.VISIBLE else View.GONE
        dictationContainer.visibility = if (index == 1) View.VISIBLE else View.GONE
        settingsContainer.visibility = if (index == 2) View.VISIBLE else View.GONE
    }

    internal fun openLocalModels() {
        navigation?.openSettings()
        settingsExtras?.toggleLocalModels()
    }

    internal fun refreshSettings() = refresh()

    // --- Model Rows ---

    private fun buildModelRow(model: Model): View {
        val radio = MaterialRadioButton(this).apply {
            isClickable = false
            buttonTintList = ColorStateList.valueOf(attrColor(androidx.appcompat.R.attr.colorPrimary))
        }
        val dlBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_ui_download)
            iconTint = ColorStateList.valueOf(ModernUi.color(this@MainActivity, R.color.primary))
            contentDescription = getString(R.string.download_model, model.name)
            setTextColor(attrColor(androidx.appcompat.R.attr.colorPrimary))
        }

        val delBtn = MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_ui_delete)
            iconTint = ColorStateList.valueOf(ModernUi.color(this@MainActivity, R.color.ink_muted))
            contentDescription = getString(R.string.delete_model, model.name)
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            visibility = View.GONE
            setOnClickListener { confirmDeleteModel(model) }
        }

        val progress = LinearProgressIndicator(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LP_MATCH, dp(4)).apply {
                topMargin = dp(8)
            }
        }

        val rightContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(delBtn)
            addView(dlBtn)
            addView(radio)
        }

        val brand = ImageView(this).apply {
            setImageResource(model.brandLogo)
            contentDescription = "${model.publisher} model family"
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(Color.WHITE) }
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) }
        }
        val row = settingsRow(
            model.name,
            modelSummary(model, false),
            rightContainer,
            brand
        ) {
            onModelAction(model)
        }

        val textContainer = row.findViewWithTag<TextView>("subtitle").parent as LinearLayout
        textContainer.addView(progress)

        modelRows[model.archive] = ModelRowViews(
            radio, progress, textContainer.findViewWithTag("subtitle"), dlBtn, delBtn
        )
        refreshCard(model)

        return row
    }

    private fun onModelAction(model: Model) {
        val views = modelRows[model.archive] ?: return

        if (ModelDownloader.isInstalled(this, model)) {
            selectModel(model.archive)
            return
        }

        views.dlBtn.isEnabled = false
        views.dlBtn.contentDescription = getString(R.string.downloading_model, model.name)
        views.progress.visibility = View.VISIBLE
        views.progress.isIndeterminate = false
        views.subtitle.text = getString(R.string.download_starting)

        ModelDownloader.download(applicationContext, model) { state ->
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                when (state) {
                    is DownloadState.Downloading -> {
                        views.progress.progress = (state.progress * 100).toInt()
                        views.subtitle.text = getString(R.string.download_progress, (state.progress * 100).toInt())
                    }
                    is DownloadState.Extracting -> {
                        views.progress.isIndeterminate = true
                        views.subtitle.text = getString(R.string.extracting)
                    }
                    is DownloadState.Done -> {
                        // Don't load the native model right after download: sherpa-onnx can
                        // SIGSEGV/SIGABRT below the JVM, which try/catch can't intercept.
                        // Keep it installed; the user taps the row to activate it.
                        views.progress.visibility = View.GONE
                        refreshAllCards()
                        refresh()
                        toast(getString(R.string.model_downloaded_tap_to_activate, model.name))
                    }
                    is DownloadState.Error -> {
                        views.progress.visibility = View.GONE
                        views.subtitle.text = getString(R.string.model_download_error, state.message)
                        views.dlBtn.isEnabled = true
                        views.dlBtn.contentDescription = getString(R.string.download_model, model.name)
                    }
                }
            }
        }
    }

    private fun selectModel(archive: String) {
        prefs().edit().putString("model_name", archive).apply()
        WhisperAccessibilityService.instance?.reloadModel()
        refreshAllCards(); refresh()
    }

    private fun refreshCard(model: Model) {
        val views = modelRows[model.archive] ?: return
        val active = prefs().getString("model_name", "") == model.archive
        val installed = ModelDownloader.isInstalled(this, model)

        views.radio.isChecked = active
        views.radio.visibility = if (installed) View.VISIBLE else View.GONE
        views.dlBtn.visibility = if (installed) View.GONE else View.VISIBLE
        views.delBtn.visibility = if (installed) View.VISIBLE else View.GONE

        if (views.progress.visibility == View.GONE) {
            val specs = modelSummary(model, installed)
            val missing = TranscriptionLanguages.saved(prefs()) - model.languages
            views.subtitle.text = if (missing.isEmpty()) specs else specs + "\n" +
                getString(R.string.model_missing_languages, missing.joinToString { TranscriptionLanguages.displayName(it) })
        }
    }

    private fun modelSummary(model: Model, installed: Boolean): String {
        val download = android.text.format.Formatter.formatShortFileSize(this, model.downloadBytes)
        val languages = if (model.languages == setOf("en")) "English only" else "Multilingual"
        val stored = if (installed) {
            val bytes = ModelDownloader.modelDir(this, model).walkTopDown().filter { it.isFile }.sumOf { it.length() }
            "\nOn device ${android.text.format.Formatter.formatShortFileSize(this, bytes)}"
        } else ""
        return "Download $download\n$languages$stored"
    }

    private fun confirmDeleteModel(model: Model) {
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_model_title, model.name))
            .setMessage(R.string.delete_model_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                val service = WhisperAccessibilityService.instance
                if (service?.canDeleteModels() == false) {
                    Toast.makeText(this, "Finish dictation before deleting a model.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                if (!ModelDownloader.delete(this, model)) {
                    Toast.makeText(this, "Couldn't delete this model. Please try again.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                service?.reloadModel()
                refreshAllCards()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshAllCards() = MODEL_CATALOG.forEach { refreshCard(it) }

    // --- State Updates ---

    private fun refresh() {
        settingsExtras?.refresh()
        val audio = hasPerm(Manifest.permission.RECORD_AUDIO)
        val acc = WhisperAccessibilityService.instance != null
        val serviceEnabled = prefs().getBoolean("service_master_enabled", true)
        val useLocal = prefs().getBoolean("use_local", true)
        val usePostProcessing = prefs().getBoolean("use_post_processing", false)
        val groqApiKey = SecureKeyStorage.groqApiKey(this)
        val cleanupSelection = runCatching { ProviderProfiles(this).cleanupSelection() }.getOrNull()
        val cleanupApiKey = cleanupSelection?.key.orEmpty()
        val hasGroqKey = groqApiKey.isNotBlank()
        val hasTranscriptionKey = runCatching { ProviderProfiles(this).speechSelection().key.isNotBlank() }.getOrDefault(false)
        val hasCleanupKey = cleanupApiKey.isNotBlank()
        val profile = DictationLanguage.fromSaved(prefs().getString("language_profile", null))
        val hasModel = MODEL_CATALOG.any { profile.acceptsModel(it.languages) && ModelDownloader.isInstalled(this, it) }
        val unrestricted = isIgnoringBatteryOptimizations()

        audioRowSub.text = if (audio) getString(R.string.granted) else getString(R.string.tap_grant_permission)
        // Three states: running, switched on but not connected (killed or frozen), or off.
        accRowSub.text = when {
            acc -> getString(R.string.enabled)
            ServiceWatchdog.isEnabledInSettings(this) -> getString(R.string.accessibility_enabled_not_running)
            else -> getString(R.string.tap_enable_settings)
        }
        batteryRowSub.text = if (unrestricted)
            getString(R.string.battery_unrestricted)
        else
            getString(R.string.battery_restriction_hint)

        // --- Setup checklist card ---
        val allOk = audio && acc && unrestricted
        val doneCount = listOf(audio, acc, unrestricted).count { it }

        setupCollapsedRow.visibility = if (allOk) View.VISIBLE else View.GONE
        setupCollapsedRowSub.text = if (setupExpanded) getString(R.string.tap_to_collapse) else getString(R.string.tap_to_review)

        setupDoneSummary.visibility = if (!allOk && doneCount > 0) View.VISIBLE else View.GONE
        setupDoneSummary.text = "✓ ${resources.getQuantityString(R.plurals.setup_steps_ready, doneCount, doneCount)}"

        fun rowVisibility(ok: Boolean) =
            if (!ok || (allOk && setupExpanded)) View.VISIBLE else View.GONE

        audioRow.visibility = rowVisibility(audio)
        accRow.visibility = rowVisibility(acc)
        accCaption.visibility = accRow.visibility
        batteryRow.visibility = rowVisibility(unrestricted)

        audioDot.background = dotDrawable(if (audio) DOT_GREEN else DOT_RED)
        accDot.background = dotDrawable(if (acc) DOT_GREEN else DOT_RED)
        batteryDot.background = dotDrawable(if (unrestricted) DOT_GREEN else DOT_RED)

        languageRowSub.text = languageSummary()
        customInstructionsRow.visibility = View.VISIBLE
        cleanupProviderRow.visibility = View.VISIBLE
        cleanupProviderRowSub.text = cleanupProviderSummary()

        keyRowSub.text = if (hasGroqKey) getString(R.string.saved_securely) else getString(R.string.key_needed_hint)

        val customInstructions = prefs().getString("custom_instructions", "") ?: ""
        customInstructionsRowSub.text = if (customInstructions.isBlank())
            getString(R.string.tap_add_refinements)
        else
            customInstructions.replace("\n", " ")

        val cur = prefs().getString("model_name", "") ?: ""
        if (cur.isBlank() || !File(filesDir, "models/$cur").exists()) {
            MODEL_CATALOG.firstOrNull { ModelDownloader.isInstalled(this, it) }
                ?.let { selectModel(it.archive) }
        }

        // Ready logic
        val localReady = useLocal && hasModel
        val cloudReady = !useLocal && hasTranscriptionKey
        val postReady = !usePostProcessing || hasCleanupKey
        val ready = audio && acc && serviceEnabled && (localReady || cloudReady) && postReady

        val (missing, action) = when {
            !serviceEnabled -> getString(R.string.status_service_paused) to null
            !audio -> getString(R.string.status_missing_audio) to null
            !acc && ServiceWatchdog.isEnabledInSettings(this) -> getString(R.string.status_accessibility_disconnected) to null
            !acc -> getString(R.string.status_missing_accessibility) to null
            useLocal && !hasModel -> getString(R.string.status_missing_model) to { openLocalModels(); Unit }
            !useLocal && !hasTranscriptionKey -> getString(R.string.status_missing_key) to { navigation?.openSettings(); settingsExtras?.openSpeech(); Unit }
            usePostProcessing && !hasCleanupKey -> getString(R.string.status_missing_cleanup_key) to { navigation?.openSettings(); settingsExtras?.openLanguageModels(); Unit }
            else -> null to null
        }
        val mode = (if (useLocal) getString(R.string.mode_on_device) else getString(R.string.mode_cloud)) +
            (if (usePostProcessing) getString(R.string.mode_cleanup_suffix) else "")
        statusSubtitle.text = if (ready) getString(R.string.status_ready_mode, mode) else missing ?: getString(R.string.setup_required)
        settingsExtras?.updateReadiness(statusSubtitle.text.toString())
        statusSubtitle.setTextColor(if (ready) attrColor(androidx.appcompat.R.attr.colorPrimary) else attrColor(android.R.attr.textColorSecondary))
        statusDotView.background = dotDrawable(if (ready) DOT_GREEN else DOT_RED)
        statusAction = if (ready) null else action
        statusRow.isClickable = statusAction != null

        // [ui] The cloud switch's subtitle says where audio goes.
        cloudRowSub.text = if (useLocal) "Off: transcribed on this phone"
                           else runCatching {
                               val selection = ProviderProfiles(this).speechSelection()
                               "On: audio is sent to ${selection.config.transcriptionUrl().host}"
                           }.getOrDefault("On: choose a valid speech provider in Settings")

        refreshAllCards()
        maybeShowBatteryWarning(acc, unrestricted)
    }

    /** Android 13+ silently disables the Accessibility toggle for apps
     * installed outside the Play Store ("Restricted settings"), with no
     * explanation in the Settings UI itself -- it just looks broken. Walks
     * the user through unlocking it before sending them to the system
     * screen, instead of letting them hit a dead end and assume the app
     * doesn't work. */
    private fun showRestrictedSettingsHelp() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.restricted_settings_title)
            .setMessage(R.string.restricted_settings_message)
            .setPositiveButton(R.string.open_accessibility_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // --- Battery optimization ---

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryExemption() {
        if (isIgnoringBatteryOptimizations()) { toast(getString(R.string.already_unrestricted)); return }
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (e: Exception) {
            // Some OEMs block the direct per-app request intent -- fall back
            // to the general battery-optimization list where the user can
            // find OpenWispr and exempt it manually.
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                toast(getString(R.string.open_battery_settings_failed, e2.message ?: getString(R.string.unknown_error)))
            }
        }
    }

    /** Nags the user, once per app-open, if the accessibility service is on
     * but Android is still free to kill it to save battery -- this is the
     * single biggest cause of the overlay silently disappearing until the
     * user re-opens the app. */
    private fun maybeShowBatteryWarning(accessibilityEnabled: Boolean, unrestricted: Boolean) {
        if (!accessibilityEnabled || unrestricted || batteryWarningShown) return
        batteryWarningShown = true
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.keep_dictation_running)
            .setMessage(R.string.battery_warning_message)
            .setPositiveButton(R.string.disable_restrictions) { _, _ -> requestBatteryExemption() }
            .setNegativeButton(getString(R.string.later), null)
            .show()
    }

    private fun promptDictionary() {
        val input = EditText(this).apply {
            hint = getString(R.string.dictionary_input_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            setText(Dictionary.load(prefs()).joinToString("\n"))
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.dictionary_title)
            .setMessage(R.string.dictionary_saved_local_message)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                Dictionary.save(prefs(), Dictionary.parse(input.text.toString()))
                WhisperAccessibilityService.instance?.reloadModel()
                toast(getString(R.string.dictionary_added))
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun promptApiKey() {
        val link = TextView(this).apply {
            text = android.text.Html.fromHtml(
                getString(R.string.groq_key_help_html),
                android.text.Html.FROM_HTML_MODE_LEGACY
            )
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            textSize = 13f
            setPadding(0, 0, 0, dp(8))
        }
        val input = EditText(this).apply {
            hint = "gsk_..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setText(SecureKeyStorage.groqApiKey(this@MainActivity))
        }
        val container = vertical(dp(24), dp(8)).apply {
            addView(link)
            addView(input)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.groq_api_key)
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                SecureKeyStorage.saveGroqApiKey(this, input.text.toString().trim())
                refresh()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun languageSummary(): String {
        val selected = TranscriptionLanguages.saved(prefs())
        if (selected.isEmpty()) return getString(R.string.transcription_languages_auto)
        return TranscriptionLanguages.ordered(selected)
            .filter { it in selected }
            .joinToString(", ") { TranscriptionLanguages.displayName(it) }
    }

    /** Searchable checklist; selected languages stay on top. Save with no
     * selection (or the Auto button) returns to auto-detect. */
    private fun promptTranscriptionLanguages() {
        val working = TranscriptionLanguages.saved(prefs()).toMutableSet()
        val search = EditText(this).apply {
            hint = getString(R.string.transcription_languages_search)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val list = vertical(0)
        fun renderList(query: String) {
            list.removeAllViews()
            val q = query.trim()
            TranscriptionLanguages.ordered(working)
                .filter { q.isEmpty() || TranscriptionLanguages.displayName(it).contains(q, ignoreCase = true) || it.contains(q, ignoreCase = true) }
                .forEach { code ->
                    list.addView(CheckBox(this).apply {
                        text = "${TranscriptionLanguages.displayName(code)} ($code)"
                        isChecked = code in working
                        setOnCheckedChangeListener { _, checked ->
                            if (checked) working.add(code) else working.remove(code)
                        }
                    })
                }
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = renderList(s?.toString().orEmpty())
        })
        renderList("")
        val container = vertical(dp(24), dp(8)).apply {
            addView(search)
            addView(ScrollView(this@MainActivity).apply {
                addView(list)
                layoutParams = LinearLayout.LayoutParams(LP_MATCH, dp(360))
            })
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.transcription_languages)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ -> saveTranscriptionLanguages(working) }
            .setNeutralButton(R.string.transcription_languages_auto) { _, _ -> saveTranscriptionLanguages(emptySet()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun showLanguageProfilePicker() {
        val values = DictationLanguage.entries
        val selected = DictationLanguage.fromSaved(prefs().getString("language_profile", null))
        val labels = values.map { profile -> when (profile) {
            DictationLanguage.AUTO -> "Auto-detect · supported languages depend on the model"
            else -> profile.title
        } }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("Dictation language")
            .setSingleChoiceItems(labels, values.indexOf(selected)) { dialog, index ->
                dialog.dismiss(); selectLanguageProfile(values[index])
            }.setNegativeButton("Cancel", null).show()
    }

    fun selectLanguageProfile(profile: DictationLanguage) {
        prefs().edit().putString("language_profile", profile.name).apply()
        val selected = profile.languageCode?.let { setOf(it) } ?: emptySet()
        saveTranscriptionLanguages(selected)
        navigation?.refresh()
    }

    internal fun refreshSettingsState() { refresh() }

    private fun saveTranscriptionLanguages(selected: Set<String>) {
        prefs().edit().putStringSet(TranscriptionLanguages.PREF_KEY, selected.toSet()).apply()
        refresh()
        if (prefs().getBoolean("use_local", true)) WhisperAccessibilityService.instance?.reloadModel()
        suggestModelForLanguages(selected)
    }

    /** Offers a local model that covers [needed] when the current one doesn't.
     * Cloud transcription already covers every Whisper language, so no prompt there. */
    private fun suggestModelForLanguages(needed: Set<String>) {
        if (needed.isEmpty() || !prefs().getBoolean("use_local", true)) return
        val current = MODEL_CATALOG.firstOrNull { it.archive == prefs().getString("model_name", "") }
        if (current?.languages?.containsAll(needed) == true) return

        val fits = MODEL_CATALOG.filter { it.languages.containsAll(needed) }
        val names = needed.joinToString { TranscriptionLanguages.displayName(it) }
        val installedFit = fits.filter { ModelDownloader.isInstalled(this, it) }
        if (installedFit.isNotEmpty()) {
            val target = installedFit.sortedByDescending { it.sizeMb }.first()
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.language_switch_title)
                .setMessage(getString(R.string.language_switch_message, target.name, names))
                .setPositiveButton(R.string.language_switch_action) { _, _ -> selectModel(target.archive) }
                .setNegativeButton(R.string.language_keep_current, null)
                .show()
        } else {
            val best = fits.sortedByDescending { it.sizeMb }.first()
            android.app.AlertDialog.Builder(this)
                .setTitle(R.string.language_no_model_title)
                .setMessage(getString(R.string.language_no_model_message, best.name, names))
                .setPositiveButton(getString(R.string.language_download_model, best.name)) { _, _ -> onModelAction(best) }
                .setNeutralButton(R.string.language_use_cloud) { _, _ ->
                    prefs().edit().putBoolean("use_local", false).apply()
                    refresh()
                }
                .setNegativeButton(R.string.language_keep_current, null)
                .show()
        }
    }

    private fun promptCleanupProvider() {
        val config = cleanupProviderConfig()
        val providers = CleanupProviderConfig.Provider.entries
        val providerPicker = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(LP_MATCH, LP_WRAP)
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                listOf(getString(R.string.groq_recommended), getString(R.string.custom_service)),
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(providers.indexOf(config.provider))
        }
        fun fieldLabel(labelRes: Int) = TextView(this).apply {
            text = getString(labelRes)
            textSize = 13f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(8), 0, dp(2))
        }
        val baseUrlLabel = fieldLabel(R.string.custom_service_url)
        val baseUrl = EditText(this).apply {
            hint = getString(R.string.openrouter_url_placeholder)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(config.baseUrl)
        }
        val modelLabel = fieldLabel(R.string.custom_service_model)
        val model = EditText(this).apply {
            hint = CleanupProviderConfig.DEFAULT_MODEL
            setText(config.model)
        }
        val apiKeyLabel = fieldLabel(R.string.custom_service_key)
        val apiKey = EditText(this).apply {
            hint = getString(R.string.api_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setText(SecureKeyStorage.cleanupApiKey(this@MainActivity))
        }
        val details = TextView(this).apply {
            text = getString(R.string.custom_service_help)
            textSize = 13f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(8), 0, 0)
        }
        val customFields = listOf<View>(baseUrlLabel, baseUrl, modelLabel, model, apiKeyLabel, apiKey, details)
        fun updateCustomFields() {
            val show = providers[providerPicker.selectedItemPosition].requiresCustomConfiguration
            customFields.forEach { it.visibility = if (show) View.VISIBLE else View.GONE }
        }
        val container = vertical(dp(24), dp(8)).apply {
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.choose_cleanup_provider)
                textSize = 14f
                setTextColor(attrColor(android.R.attr.textColorSecondary))
                setPadding(0, 0, 0, dp(8))
            })
            addView(providerPicker)
            addView(baseUrlLabel)
            addView(baseUrl)
            addView(modelLabel)
            addView(model)
            addView(apiKeyLabel)
            addView(apiKey)
            addView(details)
        }
        val scroll = ScrollView(this).apply { addView(container) }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.cleanup_provider_title)
            .setView(scroll)
            .setPositiveButton(getString(R.string.save), null)
            .setNeutralButton(R.string.clear_cleanup_key, null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.setOnShowListener {
            fun updateNeutralButton() {
                dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).visibility =
                    if (providers[providerPicker.selectedItemPosition].requiresCustomConfiguration) View.VISIBLE else View.GONE
            }
            updateCustomFields()
            updateNeutralButton()
            providerPicker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updateCustomFields()
                    updateNeutralButton()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selectedProvider = providers[providerPicker.selectedItemPosition]
                val nextConfig = CleanupProviderConfig(
                    provider = selectedProvider,
                    baseUrl = baseUrl.text.toString().trim().ifBlank { CleanupProviderConfig.DEFAULT_BASE_URL },
                    model = model.text.toString().trim().ifBlank { CleanupProviderConfig.DEFAULT_MODEL },
                )
                if (selectedProvider.requiresCustomConfiguration) {
                    try {
                        nextConfig.chatCompletionsUrl()
                    } catch (_: IllegalArgumentException) {
                        toast(getString(R.string.invalid_cleanup_url))
                        return@setOnClickListener
                    }
                }
                ProviderProfiles.locked {
                prefs().edit()
                    .remove(ProviderProfiles.CLEANUP_ID)
                    .putString("cleanup_provider", selectedProvider.preferenceValue)
                    .putString("cleanup_base_url", nextConfig.baseUrl)
                    .putString("cleanup_model", nextConfig.model)
                    .apply()
                if (selectedProvider.requiresCustomConfiguration) {
                    apiKey.text.toString().trim().takeIf { it.isNotBlank() }
                        ?.let { SecureKeyStorage.saveCleanupApiKey(this, it) }
                }
                }
                dialog.dismiss()
                refresh()
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                SecureKeyStorage.saveCleanupApiKey(this, "")
                apiKey.text.clear()
                toast(getString(R.string.cleanup_api_key_cleared))
                refresh()
            }
        }
        dialog.show()
    }

    private fun promptCustomInstructions() {
        // Custom refinements append to the fixed faithful cleanup rules.
        val input = EditText(this).apply {
            hint = getString(R.string.custom_instructions_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            setHorizontallyScrolling(false)
            gravity = Gravity.TOP or Gravity.START
            setText(prefs().getString("custom_instructions", ""))
        }
        val content = vertical(0, 0).apply {
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.custom_instructions_help)
                textSize = 14f
                setTextColor(attrColor(android.R.attr.textColorSecondary))
                setPadding(0, 0, 0, dp(8))
            })
            addView(input)
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_custom_instructions))
            .setView(scrollableDialogContent(content))
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                prefs().edit().putString("custom_instructions", input.text.toString().trim()).apply()
                refresh()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .setNeutralButton("Reset to default") { _, _ -> prefs().edit().remove("custom_instructions").apply(); refresh() }
            .create()
        showResizingDialog(dialog)
    }

    // --- UI Helpers ---

    private fun settingsRow(
        title: String,
        subtitle: String,
        widget: View? = null,
        leading: View? = null,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
            isClickable = onClick != null
            isFocusable = onClick != null
            if (onClick != null) {
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener { onClick() }
            }
        }

        if (leading != null) row.addView(leading)

        val textContainer = vertical(0).apply {
            layoutParams = LinearLayout.LayoutParams(0, LP_WRAP, 1f).apply {
                // [ui] Keep wrapped text clear of the switch/button beside it.
                if (widget != null) marginEnd = dp(16)
            }
        }

        textContainer.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(attrColor(android.R.attr.textColorPrimary))
        })

        textContainer.addView(TextView(this).apply {
            tag = "subtitle"
            text = subtitle
            textSize = 14f
            setTextColor(attrColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(2), 0, 0)
        })

        row.addView(textContainer)
        if (widget != null) row.addView(widget)

        return row
    }

    private fun sectionHeader(title: String) = TextView(this).apply {
        text = title
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(attrColor(androidx.appcompat.R.attr.colorPrimary)) // Neutral Android-like blue
        // [ui] 40dp start = group() margin (16) + row padding (24), so the
        // header lines up with the row text inside the panel below it.
        setPadding(dp(40), dp(24), dp(24), dp(8))
    }

    /** [ui] A section panel: rounded, in the theme's surface-container
     * colour (light/dark aware), clipping row ripples to its corners. */
    private fun group() = vertical(0).apply {
        background = GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(attrColor(com.google.android.material.R.attr.colorSurfaceContainer))
        }
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(LP_MATCH, LP_WRAP).apply {
            setMargins(dp(16), 0, dp(16), dp(8))
        }
    }

    private fun scrollableDialogContent(content: View) = ScrollView(this).apply {
        isFillViewport = true
        setPadding(dp(24), dp(8), dp(24), dp(8))
        addView(content, FrameLayout.LayoutParams(LP_MATCH, LP_WRAP))
    }

    private fun showResizingDialog(dialog: android.app.AlertDialog) {
        dialog.show()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun vertical(padH: Int, padV: Int = padH) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padH, padV, padH, padV)
    }

    private fun dotDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun statusDot(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
            marginEnd = dp(12)
        }
        background = dotDrawable(DOT_RED)
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun attrColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }
    private fun cleanupProviderConfig() = CleanupProviderConfig.fromPreferences(
        prefs().getString("cleanup_provider", null),
        prefs().getString("cleanup_base_url", null),
        prefs().getString("cleanup_model", null),
    )

    private fun cleanupProviderSummary(): String {
        val profiles = ProviderProfiles(this)
        if (profiles.activeCleanupId() != null) {
            return runCatching { val selection = profiles.cleanupSelection()
                "${selection.config.model} · ${if (selection.key.isBlank()) "Add API key" else "Saved provider"}" }
                .getOrDefault("Selected provider needs attention")
        }
        val config = cleanupProviderConfig()
        val configured = when (config.provider) {
            CleanupProviderConfig.Provider.GROQ -> SecureKeyStorage.groqApiKey(this).isNotBlank()
            CleanupProviderConfig.Provider.OPENAI_COMPATIBLE -> SecureKeyStorage.cleanupApiKey(this).isNotBlank()
        }
        val summary = when (config.provider) {
            CleanupProviderConfig.Provider.GROQ ->
                if (configured) R.string.cleanup_service_groq_ready else R.string.cleanup_service_groq_setup
            CleanupProviderConfig.Provider.OPENAI_COMPATIBLE ->
                if (configured) R.string.cleanup_service_custom_ready else R.string.cleanup_service_custom_setup
        }
        return getString(summary)
    }

    private fun currentBubbleSizePercent(): Int {
        val storedValue = try {
            prefs().getInt(BubbleSize.PREFERENCE_KEY, BubbleSize.DEFAULT_PERCENT)
        } catch (_: ClassCastException) {
            null
        }
        return BubbleSize.preferencePercent(storedValue)
    }
    private fun localizedBubbleLabel(percent: Int) = BubbleSize.valueLabel(
        percent,
        standard = getString(R.string.bubble_standard),
        smaller = getString(R.string.bubble_smaller),
        larger = getString(R.string.bubble_larger),
    )
    private fun localizedBubbleDescription(percent: Int) = BubbleSize.accessibilityDescription(
        percent,
        standard = getString(R.string.bubble_standard_accessibility),
        smaller = getString(R.string.bubble_smaller_accessibility),
        larger = getString(R.string.bubble_larger_accessibility),
        percentUnit = getString(R.string.percent_unit),
    )
    private fun prefs() = getSharedPreferences("openwhispr", MODE_PRIVATE)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // --- Backup and restore ---

    private fun prefItemId(key: String) = "pref:$key"
    private fun secretItemId(name: String) = "secret:$name"
    private fun secretLabel(name: String) = when (name) {
        "groq_api_key" -> getString(R.string.backup_item_groq_key)
        "cleanup_api_key" -> getString(R.string.backup_item_cleanup_key)
        else -> name
    }

    private fun currentSnapshot(): SettingsBackup.Snapshot = SettingsBackup.Snapshot(
        prefs = prefs().all.entries.mapNotNull { (key, value) -> value?.let { key to it } }.toMap(),
        secrets = mapOf(
            "transcription_api_key" to SecureKeyStorage.transcriptionApiKey(this),
            "groq_api_key" to SecureKeyStorage.groqApiKey(this),
            "cleanup_api_key" to SecureKeyStorage.cleanupApiKey(this),
        ) + ProviderProfiles(this).exportKeys(),
        snippets = SnippetRepository(this).use { it.all() },
    )

    /** Checklist with "select all"; [onConfirm] receives the chosen item ids. */
    private fun chooseBackupItems(title: Int, items: List<Pair<String, String>>, onConfirm: (Set<String>) -> Unit) {
        if (items.isEmpty()) {
            toast(getString(R.string.backup_nothing_to_choose))
            return
        }
        val selected = items.map { it.first }.filterNot { it.startsWith("secret:") }.toMutableSet()
        val boxes = mutableListOf<CheckBox>()
        var syncing = false
        val selectAll = CheckBox(this).apply {
            text = getString(R.string.backup_select_all)
            isChecked = selected.size == items.size
            setOnCheckedChangeListener { _, checked ->
                if (syncing) return@setOnCheckedChangeListener
                syncing = true
                boxes.forEach { it.isChecked = checked }
                syncing = false
            }
        }
        val list = vertical(0)
        items.forEach { (id, label) ->
            val box = CheckBox(this).apply {
                text = label
                isChecked = id in selected
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(id) else selected.remove(id)
                    if (!syncing) {
                        syncing = true
                        selectAll.isChecked = boxes.all { it.isChecked }
                        syncing = false
                    }
                }
            }
            boxes += box
            list.addView(box)
        }
        val container = vertical(dp(24), dp(8)).apply {
            addView(selectAll)
            addView(ScrollView(this@MainActivity).apply {
                addView(list)
                layoutParams = LinearLayout.LayoutParams(LP_MATCH, dp(360))
            })
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(R.string.continue_button) { _, _ ->
                if (selected.isEmpty()) toast(getString(R.string.backup_nothing_selected))
                else onConfirm(selected.toSet())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Passphrase prompt. Exports ask twice and enforce a minimum length. */
    private fun askPassphrase(exporting: Boolean, onOk: (String) -> Unit) {
        fun field(hintRes: Int) = EditText(this).apply {
            hint = getString(hintRes)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        val first = field(R.string.backup_passphrase_hint)
        val second = field(R.string.backup_passphrase_confirm_hint)
        val container = vertical(dp(24), dp(8)).apply {
            addView(first)
            if (exporting) addView(second)
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(if (exporting) R.string.backup_passphrase_title_export else R.string.backup_passphrase_title_restore)
            .setView(container)
            .setPositiveButton(if (exporting) R.string.save else R.string.continue_button) { _, _ ->
                val passphrase = first.text.toString()
                when {
                    exporting && passphrase.length < SettingsBackup.MIN_PASSPHRASE_LENGTH ->
                        toast(getString(R.string.backup_passphrase_short))
                    exporting && passphrase != second.text.toString() ->
                        toast(getString(R.string.backup_passphrase_mismatch))
                    !exporting && passphrase.isEmpty() ->
                        toast(getString(R.string.backup_passphrase_short))
                    else -> onOk(passphrase)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportBackup() {
        val snapshot = currentSnapshot()
        val items = snapshot.prefs.keys.filterNot { SettingsBackup.isExcludedPreference(it) }.sorted()
            .map { prefItemId(it) to it } +
            snapshot.secrets.filterValues { it.isNotBlank() }.keys.sorted()
                .map { secretItemId(it) to secretLabel(it) } +
            listOf("data:snippets" to "Saved snippets (text)")
        chooseBackupItems(R.string.backup_choose_export, items) { chosen ->
            askPassphrase(exporting = true) { passphrase ->
                backupPassphrase = passphrase
                pendingExport = SettingsBackup.Snapshot(
                    prefs = snapshot.prefs.filterKeys { prefItemId(it) in chosen },
                    secrets = snapshot.secrets.filterKeys { secretItemId(it) in chosen },
                    snippets = if ("data:snippets" in chosen) snapshot.snippets else null,
                )
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                createBackupLauncher.launch("openwispr-backup-$date.owb")
            }
        }
    }

    private fun writeBackup(uri: Uri) {
        val passphrase = backupPassphrase
        val snapshot = pendingExport
        backupPassphrase = null
        pendingExport = null
        if (passphrase == null || snapshot == null) return
        kotlin.concurrent.thread {
            try {
                val file = SettingsBackup.encrypt(snapshot, passphrase)
                contentResolver.openOutputStream(uri)?.use { it.write(file.toByteArray(Charsets.UTF_8)) }
                    ?: throw java.io.IOException("cannot open the file")
                runOnUiThread { toast(getString(R.string.backup_export_done)) }
            } catch (e: Exception) {
                runOnUiThread { toast(getString(R.string.backup_export_failed, e.message ?: "")) }
            }
        }
    }

    private fun readBackup(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            null
        }
        if (text == null) {
            toast(getString(R.string.backup_restore_failed))
            return
        }
        askPassphrase(exporting = false) { passphrase ->
            kotlin.concurrent.thread {
                try {
                    val snapshot = SettingsBackup.decrypt(text, passphrase)
                    runOnUiThread { chooseRestoreItems(snapshot) }
                } catch (e: SettingsBackup.BackupException) {
                    runOnUiThread { toast(getString(R.string.backup_restore_failed)) }
                }
            }
        }
    }

    private fun chooseRestoreItems(snapshot: SettingsBackup.Snapshot) {
        val items = snapshot.prefs.keys.sorted().map { prefItemId(it) to it } +
            snapshot.secrets.keys.sorted().map { secretItemId(it) to secretLabel(it) } +
            (if (snapshot.snippets == null) emptyList() else listOf("data:snippets" to "Saved snippets (text)"))
        chooseBackupItems(R.string.backup_choose_restore, items) { chosen ->
            applySelected(snapshot, chosen)
        }
    }

    /** Writes only the chosen settings and keys; everything else on the device is left as it is. */
    private fun applySelected(snapshot: SettingsBackup.Snapshot, chosen: Set<String>) {
        fun writeSettings(values: Map<String, Any?>) {
            check(prefs().edit().apply {
                for ((key, value) in values) when (value) {
                    null -> remove(key)
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value.map { it.toString() }.toSet())
                }
            }.commit())
        }
        try { ProviderProfiles.locked {
            val selectedSecrets = snapshot.secrets.filterKeys { name ->
                secretItemId(name) in chosen && (name in setOf("transcription_api_key", "groq_api_key", "cleanup_api_key") ||
                    name.matches(Regex("provider_profile_[a-f0-9]{32}")))
            }
            SecureKeyStorage.groqApiKey(this) // Complete any legacy migration before rollback snapshots.
            val original = prefs().all
            val changes = ProviderProfileRestore.prepare(original, snapshot.prefs,
                snapshot.prefs.keys.filter { prefItemId(it) in chosen }.toSet(), selectedSecrets.keys) + ("welcome_done" to true)
            val oldSettings = changes.keys.associateWith { original[it] }
            val oldSecrets = SecureKeyStorage.encryptedEntries(this, selectedSecrets.keys)
            val oldSnippets = if ("data:snippets" in chosen && snapshot.snippets != null) SnippetRepository(this).use { it.all() } else null
            try {
                writeSettings(changes)
                for ((name, value) in selectedSecrets) when (name) {
                    "transcription_api_key" -> SecureKeyStorage.saveTranscriptionApiKey(this, value)
                    "groq_api_key" -> SecureKeyStorage.saveGroqApiKey(this, value)
                    "cleanup_api_key" -> SecureKeyStorage.saveCleanupApiKey(this, value)
                    else -> SecureKeyStorage.saveProfileApiKey(this, name.removePrefix("provider_profile_"), value)
                }
                if (oldSnippets != null) SnippetRepository(this).use { it.replaceAll(requireNotNull(snapshot.snippets)) }
            } catch (failure: Exception) {
                try { SecureKeyStorage.restoreEncryptedEntries(this, oldSecrets) } finally {
                    try { writeSettings(oldSettings) } finally { if (oldSnippets != null) SnippetRepository(this).use { it.replaceAll(oldSnippets) } }
                }
                throw failure
            }
        } } catch (_: Exception) { toast("Couldn’t restore the chosen settings. Include saved profiles with their selections and matching keys for changed URLs."); return }
        WhisperAccessibilityService.instance?.apply {
            refreshBubbleSize()
            refreshPosition()
            reloadModel()
        }
        toast(getString(R.string.backup_restore_done))
        recreate()
    }

    private fun showWelcome() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.welcome_title)
            .setMessage(R.string.welcome_message)
            .setPositiveButton(R.string.welcome_restore) { _, _ ->
                prefs().edit().putBoolean("welcome_done", true).apply()
                openBackupLauncher.launch(arrayOf("*/*"))
            }
            .setNegativeButton(R.string.welcome_start_fresh) { _, _ ->
                prefs().edit().putBoolean("welcome_done", true).apply()
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        private const val LP_MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        private const val LP_WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        private const val DOT_GREEN = 0xFF34C759.toInt()
        private const val DOT_RED = 0xFFEF4444.toInt()
    }
}



