package com.shrey.cashflowatlas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shrey.cashflowatlas.analytics.FinanceAnalyzer
import com.shrey.cashflowatlas.data.CashflowStore
import com.shrey.cashflowatlas.model.CategoryRule
import com.shrey.cashflowatlas.model.FinanceProfile
import com.shrey.cashflowatlas.model.FinanceState
import com.shrey.cashflowatlas.model.Transaction
import com.shrey.cashflowatlas.sms.SmsIntelligence
import com.shrey.cashflowatlas.sms.SmsParser
import com.shrey.cashflowatlas.sms.SmsReader
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private enum class CashflowTab(val label: String, val title: String) {
    TODAY("Home", "Cashflow"),
    SMS("Review", "Review inbox"),
    ATLAS("Insights", "Insights"),
    CUSTOMIZE("Settings", "Security")
}

class MainActivity : ComponentActivity() {
    private companion object {
        private const val SAMPLE_SMS =
            "INR 92000 credited to A/c XX4321 by ACME PAYROLL on 01-May. Avl Bal INR 154000.\n" +
                "Rs. 849 debited from HDFC Bank card at ZOMATO on 02-May. Not you? Call bank.\n" +
                "UPI payment of INR 420 sent to SWIGGY from A/c XX4321 on 03-May. Ref 421882.\n" +
                "Rs. 2500 debited at AMAZON from ICICI card on 05-May.\n" +
                "INR 1800 debited from account towards UBER TRIP on 06-May.\n" +
                "Rs. 7000 credited via refund from AMAZON on 08-May.\n" +
                "INR 1499 debited for AIRTEL FIBER bill on 09-May.\n" +
                "Congratulations! You won Rs 5000 reward. Click http://tinyurl.fake/claim now.\n" +
                "OTP 492881 for account login. Do not share this code with anyone."
    }

    private lateinit var store: CashflowStore
    private val analyzer = FinanceAnalyzer()
    private val parser = SmsParser()
    private val smsReader = SmsReader()
    private val smsPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsAllowed = granted
        if (granted) {
            scanSms()
        } else {
            toast("SMS permission denied. Manual paste still works.")
        }
    }

    private var state by mutableStateOf(FinanceState.demo())
    private var parsedTransactions by mutableStateOf<List<Transaction>>(emptyList())
    private var rejectedSms by mutableStateOf<List<SmsIntelligence.Review>>(emptyList())
    private var scannedSmsCount by mutableStateOf(0)
    private var atlasMode by mutableStateOf(AtlasView.Mode.FLOW)
    private var smsAllowed by mutableStateOf(false)
    private var privacyMask by mutableStateOf(false)
    private var activeTab by mutableStateOf(CashflowTab.TODAY)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        store = CashflowStore(this)
        state = store.load()
        smsAllowed = hasSmsPermission()

        setContent {
            CashflowTheme {
                CashflowScreen(
                    state = state,
                    summary = analyzer.analyze(state),
                    parsedTransactions = parsedTransactions,
                    rejectedSms = rejectedSms,
                    scannedSmsCount = scannedSmsCount,
                    activeTab = activeTab,
                    atlasMode = atlasMode,
                    smsAllowed = smsAllowed,
                    privacyMask = privacyMask,
                    sampleSms = SAMPLE_SMS,
                    onTab = { activeTab = it },
                    onScanSms = ::requestOrScanSms,
                    onUseSample = ::parseSample,
                    onParseManual = ::parseManual,
                    onImportParsed = ::importParsed,
                    onAtlasMode = { atlasMode = it },
                    onPrivacyMask = { privacyMask = it },
                    onClearDemo = ::clearDemoRows,
                    onClearImported = ::clearImportedRows,
                    onAddManual = ::addManualTransaction,
                    onAddCategory = ::addCategory,
                    onUpdateCategory = ::updateCategory,
                    onRemoveCategory = ::removeCategory,
                    onUpdateProfile = ::updateProfile,
                    onReset = ::resetDemo
                )
            }
        }
    }

    private fun hasSmsPermission(): Boolean =
        checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun requestOrScanSms() {
        if (!hasSmsPermission()) {
            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
            return
        }
        scanSms()
    }

    private fun scanSms() {
        runCatching {
            applySmsBatch(smsReader.readRecentInbox(this, 250))
        }.onFailure {
            parsedTransactions = emptyList()
            rejectedSms = emptyList()
            scannedSmsCount = 0
            toast("SMS scan failed. Paste messages manually instead.")
        }
    }

    private fun parseSample() {
        applySmsBatch(SAMPLE_SMS.split(Regex("\\n+")))
    }

    private fun parseManual(raw: String) {
        applySmsBatch(raw.split(Regex("\\n+")))
    }

    private fun applySmsBatch(messages: List<String>) {
        val batch = parser.parseBatch(messages, state.categories)
        parsedTransactions = batch.transactions
        rejectedSms = batch.rejected
        scannedSmsCount = batch.total
        activeTab = CashflowTab.SMS
        atlasMode = AtlasView.Mode.MERCHANTS
        toast("${batch.transactions.size} accepted, ${batch.rejected.size} discarded")
    }

    private fun importParsed() {
        if (parsedTransactions.isEmpty()) {
            toast("Nothing parsed yet")
            return
        }
        val existing = state.transactions.map(::dedupeKey).toMutableSet()
        val cleanTransactions = state.transactions.filterNot { it.source == "demo" }.toMutableList()
        var imported = 0
        parsedTransactions.forEach { transaction ->
            val key = dedupeKey(transaction)
            if (existing.add(key)) {
                cleanTransactions.add(0, transaction)
                imported += 1
            }
        }
        state.transactions = cleanTransactions
        parsedTransactions = emptyList()
        rejectedSms = emptyList()
        scannedSmsCount = 0
        activeTab = CashflowTab.ATLAS
        atlasMode = AtlasView.Mode.MERCHANTS
        publish("$imported SMS transactions imported into analytics")
    }

    private fun clearDemoRows() {
        state.transactions = state.transactions.filterNot { it.source == "demo" }.toMutableList()
        publish("Demo rows removed")
    }

    private fun clearImportedRows() {
        state.transactions = state.transactions.filterNot { it.source == "sms" }.toMutableList()
        publish("Imported SMS rows cleared")
    }

    private fun addManualTransaction(name: String, amount: Double, type: String, categoryLabel: String) {
        if (name.isBlank() || amount <= 0) return
        val categoryId = if (type == "credit") "income" else categoryIdFromLabel(categoryLabel)
        state.transactions.add(
            0,
            Transaction(
                "manual-${System.currentTimeMillis()}",
                LocalDate.now().toString(),
                type,
                name.trim(),
                categoryId,
                amount,
                "manual",
                "",
                100
            )
        )
        publish("Transaction added")
    }

    private fun addCategory(label: String, budget: Double, keywords: String, essential: Boolean) {
        if (label.isBlank()) return
        state.categories.add(
            CategoryRule(
                slug(label),
                label.trim(),
                budget,
                randomCategoryColor(),
                essential,
                splitKeywords(keywords)
            )
        )
        publish("${label.trim()} added")
    }

    private fun updateCategory(category: CategoryRule, budget: Double, keywords: String, essential: Boolean) {
        category.budget = budget
        category.keywords = splitKeywords(keywords)
        category.essential = essential
        publish("${category.label} updated")
    }

    private fun updateProfile(monthlyIncome: Double, emergencyFund: Double, savingsGoal: Double, monthlySaveTarget: Double) {
        state.profile = FinanceProfile(monthlyIncome, emergencyFund, savingsGoal, monthlySaveTarget)
        publish("Profile updated")
    }

    private fun removeCategory(category: CategoryRule) {
        state.categories.remove(category)
        state.transactions.forEach { if (it.category == category.id) it.category = "shopping" }
        publish("${category.label} removed")
    }

    private fun resetDemo() {
        store.reset()
        state = FinanceState.demo()
        parsedTransactions = emptyList()
        rejectedSms = emptyList()
        scannedSmsCount = 0
        activeTab = CashflowTab.TODAY
        atlasMode = AtlasView.Mode.FLOW
        toast("Reset complete")
    }

    private fun publish(message: String) {
        store.save(state)
        state = FinanceState(state.profile, state.categories, state.transactions)
        toast(message)
    }

    private fun categoryIdFromLabel(label: String): String =
        state.categories.firstOrNull { it.label == label }?.id ?: "shopping"

    private fun dedupeKey(transaction: Transaction): String =
        "${transaction.type}|${transaction.name}|${transaction.amount.toLong()}|${transaction.date}"

    private fun slug(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "custom" }

    private fun splitKeywords(value: String): List<String> =
        value.split(",").map { it.trim() }.filter { it.isNotBlank() }

    private fun randomCategoryColor(): Int {
        val palette = listOf(0xff2563eb, 0xff059669, 0xffea580c, 0xffbe123c, 0xff7c3aed, 0xff0f766e)
        return palette[state.categories.size % palette.size].toInt()
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

@Composable
private fun CashflowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xff05603a),
            secondary = Color(0xff2563eb),
            tertiary = Color(0xffea580c),
            surface = Color(0xffffffff),
            background = Color(0xffeef2f6),
            onSurface = Color(0xff172033)
        ),
        content = content
    )
}

@Composable
private fun CashflowScreen(
    state: FinanceState,
    summary: FinanceAnalyzer.Summary,
    parsedTransactions: List<Transaction>,
    rejectedSms: List<SmsIntelligence.Review>,
    scannedSmsCount: Int,
    activeTab: CashflowTab,
    atlasMode: AtlasView.Mode,
    smsAllowed: Boolean,
    privacyMask: Boolean,
    sampleSms: String,
    onTab: (CashflowTab) -> Unit,
    onScanSms: () -> Unit,
    onUseSample: () -> Unit,
    onParseManual: (String) -> Unit,
    onImportParsed: () -> Unit,
    onAtlasMode: (AtlasView.Mode) -> Unit,
    onPrivacyMask: (Boolean) -> Unit,
    onClearDemo: () -> Unit,
    onClearImported: () -> Unit,
    onAddManual: (String, Double, String, String) -> Unit,
    onAddCategory: (String, Double, String, Boolean) -> Unit,
    onUpdateCategory: (CategoryRule, Double, String, Boolean) -> Unit,
    onRemoveCategory: (CategoryRule) -> Unit,
    onUpdateProfile: (Double, Double, Double, Double) -> Unit,
    onReset: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xffeef2f6),
        topBar = { AppTopBar(activeTab, summary, privacyMask, onPrivacyMask) },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding(), containerColor = Color(0xffffffff)) {
                CashflowTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { onTab(tab) },
                        icon = { Icon(tabIcon(tab), contentDescription = null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeTab != CashflowTab.SMS) {
                FloatingActionButton(onClick = onScanSms, containerColor = Color(0xff05603a), contentColor = Color.White) {
                    Icon(Icons.Rounded.Email, contentDescription = "Analyze SMS")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (
                        slideInHorizontally(animationSpec = tween(260)) { width -> width * direction } + fadeIn(tween(180))
                    ).togetherWith(
                        slideOutHorizontally(animationSpec = tween(220)) { width -> -width * direction } + fadeOut(tween(160))
                    ).using(SizeTransform(clip = false))
                },
                label = "cashflow-tab"
            ) { tab ->
                Column(
                    modifier = Modifier.animateContentSize(animationSpec = tween(260)),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (tab) {
                        CashflowTab.TODAY -> TodayWorkspace(summary, parsedTransactions, rejectedSms, scannedSmsCount, smsAllowed, onScanSms, onImportParsed, onClearDemo)
                        CashflowTab.SMS -> SmsWorkspace(summary, parsedTransactions, rejectedSms, scannedSmsCount, smsAllowed, sampleSms, onScanSms, onUseSample, onParseManual, onImportParsed, onClearDemo)
                        CashflowTab.ATLAS -> AtlasWorkspace(state, summary, atlasMode, privacyMask, onAtlasMode, onPrivacyMask)
                        CashflowTab.CUSTOMIZE -> CustomizeWorkspace(state, summary, privacyMask, onPrivacyMask, onUpdateProfile, onAddCategory, onUpdateCategory, onRemoveCategory, onAddManual, onClearImported, onReset)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(activeTab: CashflowTab, summary: FinanceAnalyzer.Summary, privacyMask: Boolean, onPrivacyMask: (Boolean) -> Unit) {
    Surface(color = Color(0xff172033), contentColor = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = healthColor(summary.healthScore), modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(summary.healthScore.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(activeTab.title, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${summary.healthLabel} | ${summary.importedSmsCount} SMS | ${summary.daysLeft} days left", color = Color(0xffcbd5e1), fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { onPrivacyMask(!privacyMask) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (privacyMask) "Masked" else "Visible", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun TodayWorkspace(
    summary: FinanceAnalyzer.Summary,
    parsedTransactions: List<Transaction>,
    rejectedSms: List<SmsIntelligence.Review>,
    scannedSmsCount: Int,
    smsAllowed: Boolean,
    onScanSms: () -> Unit,
    onImportParsed: () -> Unit,
    onClearDemo: () -> Unit
) {
    CommandHero(summary, parsedTransactions, smsAllowed, onScanSms, onImportParsed, onClearDemo)
    AiCopilotPanel(summary, parsedTransactions, rejectedSms)
    AiShieldCompact(rejectedSms, scannedSmsCount)
    ActionQueue(summary)
}

@Composable
private fun CommandHero(
    summary: FinanceAnalyzer.Summary,
    parsedTransactions: List<Transaction>,
    smsAllowed: Boolean,
    onScanSms: () -> Unit,
    onImportParsed: () -> Unit,
    onClearDemo: () -> Unit
) {
    AppCard(kicker = "Snapshot", title = "This month") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HealthGauge(summary.healthScore, summary.healthLabel, Modifier.weight(0.9f))
            Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BigNumber("Free cash", money(summary.freeCash), summary.freeCash >= 0)
                BigNumber("Safe/day", money(summary.dailySafeLimit), summary.dailySafeLimit > 0)
            }
        }
        MetricGrid(
            listOf(
                "Actual spend" to money(summary.actualSpend),
                "Forecast" to money(summary.forecastedMonthlySpend),
                "Budget used" to "${summary.budgetUtilization}%",
                "Runway" to "${summary.runwayMonths} mo"
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onScanSms, modifier = Modifier.weight(1.2f)) {
                Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (smsAllowed) "Analyze SMS" else "Grant SMS")
            }
            Button(
                onClick = onImportParsed,
                enabled = parsedTransactions.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xff2563eb))
            ) {
                Text("Import ${parsedTransactions.size}")
            }
        }
        if (summary.demoTransactionCount > 0) {
            WarningPanel("Demo rows are still mixed in. Import real SMS or clear them for a real view.")
            OutlinedButton(onClick = onClearDemo, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Clear demo rows")
            }
        }
    }
}

@Composable
private fun ActionQueue(summary: FinanceAnalyzer.Summary) {
    AppCard(kicker = "Next best actions", title = "Rule-based signals") {
        ActionItem(
            icon = if (summary.healthScore >= 82) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            title = "Cashflow health",
            body = "${summary.healthLabel} at ${summary.healthScore}/100. Budget use is ${summary.budgetUtilization}%."
        )
        if (summary.mostOverAmount > 0) {
            ActionItem(Icons.Rounded.Warning, "Budget pressure", "${summary.mostOverCategory} is over budget by ${money(summary.mostOverAmount)}.")
        } else {
            ActionItem(Icons.Rounded.CheckCircle, "Budget pressure", "No category has crossed its budget yet.")
        }
        if (summary.topMerchantSpend > 0) {
            ActionItem(Icons.Rounded.PieChart, "Merchant concentration", "${summary.topMerchant} is ${summary.merchantConcentration}% of tracked spend.")
        }
        if (summary.anomalies.isNotEmpty()) {
            summary.anomalies.take(3).forEach { anomaly ->
                ActionItem(Icons.Rounded.Warning, "Risk check", anomaly.message)
            }
        }
    }
}

@Composable
private fun SavingsSimulator(summary: FinanceAnalyzer.Summary) {
    var cutPercent by remember { mutableFloatStateOf(15f) }
    val extraSavings = summary.discretionarySpend * (cutPercent / 100.0)
    val simulatedFreeCash = summary.freeCash + extraSavings
    val currentEta = if (summary.goalEtaMonths > 0) "${summary.goalEtaMonths} mo" else "blocked"
    val simulatedEta = if (simulatedFreeCash > 0) "${kotlin.math.ceil(summary.savingsGoal / simulatedFreeCash).toInt()} mo" else "blocked"
    AppCard(kicker = "Scenario lab", title = "Discretionary cut simulator") {
        Text("Cut ${cutPercent.roundToInt()}% of discretionary spend", color = Color(0xff334155), fontWeight = FontWeight.Bold)
        Slider(value = cutPercent, onValueChange = { cutPercent = it }, valueRange = 0f..50f)
        MetricGrid(
            listOf(
                "Extra saved" to money(extraSavings),
                "Free cash" to money(simulatedFreeCash),
                "Current ETA" to currentEta,
                "Sim ETA" to simulatedEta
            )
        )
        Text("This simulator uses your current discretionary spend and does not mutate saved data.", color = Color(0xff64748b), fontSize = 12.sp)
    }
}

@Composable
private fun SmsWorkspace(
    summary: FinanceAnalyzer.Summary,
    parsedTransactions: List<Transaction>,
    rejectedSms: List<SmsIntelligence.Review>,
    scannedSmsCount: Int,
    smsAllowed: Boolean,
    sampleSms: String,
    onScanSms: () -> Unit,
    onUseSample: () -> Unit,
    onParseManual: (String) -> Unit,
    onImportParsed: () -> Unit,
    onClearDemo: () -> Unit
) {
    SmsCommandCenter(summary, parsedTransactions, rejectedSms, scannedSmsCount, smsAllowed, sampleSms, onScanSms, onUseSample, onParseManual, onImportParsed, onClearDemo)
    AiCopilotPanel(summary, parsedTransactions, rejectedSms)
    SmsShieldPanel(rejectedSms, scannedSmsCount)
    DataQualityPanel(summary, rejectedSms)
}

@Composable
private fun SmsCommandCenter(
    summary: FinanceAnalyzer.Summary,
    parsedTransactions: List<Transaction>,
    rejectedSms: List<SmsIntelligence.Review>,
    scannedSmsCount: Int,
    smsAllowed: Boolean,
    sampleSms: String,
    onScanSms: () -> Unit,
    onUseSample: () -> Unit,
    onParseManual: (String) -> Unit,
    onImportParsed: () -> Unit,
    onClearDemo: () -> Unit
) {
    var manualSms by remember { mutableStateOf(sampleSms) }
    AppCard(kicker = "Ingestion", title = if (summary.importedSmsCount > 0) "SMS is powering analytics" else "Connect local SMS") {
        MetricGrid(
            listOf(
                "SMS spend" to money(summary.importedSmsSpend),
                "SMS credits" to money(summary.importedSmsCredits),
                "Queue" to parsedTransactions.size.toString(),
                "Discarded" to rejectedSms.size.toString(),
                "Scanned" to scannedSmsCount.toString(),
                "Signal" to "${summary.averageConfidence.toInt()}%"
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onScanSms, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (smsAllowed) "Scan inbox" else "Grant & scan")
            }
            OutlinedButton(onClick = onUseSample, modifier = Modifier.weight(1f)) {
                Text("Load sample")
            }
        }
        OutlinedTextField(
            value = manualSms,
            onValueChange = { manualSms = it },
            label = { Text("Paste bank SMS alerts") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onParseManual(manualSms) }, modifier = Modifier.weight(1f)) {
                Text("Parse")
            }
            Button(onClick = onImportParsed, enabled = parsedTransactions.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Import ${parsedTransactions.size}")
            }
        }
        if (summary.demoTransactionCount > 0) {
            OutlinedButton(onClick = onClearDemo, modifier = Modifier.fillMaxWidth()) {
                Text("Remove demo rows")
            }
        }
        if (parsedTransactions.isNotEmpty()) {
            Text("Review before import", fontWeight = FontWeight.Black, color = Color(0xff172033))
            parsedTransactions.take(6).forEach { transaction -> TransactionPreview(transaction) }
            if (parsedTransactions.size > 6) Text("+${parsedTransactions.size - 6} more ready", color = Color(0xff64748b))
        }
    }
}

@Composable
private fun DataQualityPanel(summary: FinanceAnalyzer.Summary, rejectedSms: List<SmsIntelligence.Review>) {
    AppCard(kicker = "Privacy and quality", title = "Local-only data controls") {
        ActionItem(Icons.Rounded.AutoAwesome, "Local AI shield", "${rejectedSms.size} fake, invalid, OTP, promo, or spam-like message(s) blocked before import.")
        ActionItem(Icons.Rounded.Lock, "No network permission", "The app manifest has no INTERNET permission, so SMS data cannot be uploaded by this app.")
        ActionItem(Icons.Rounded.CheckCircle, "Data confidence", "Average parser confidence is ${summary.averageConfidence}%. Low-confidence rows stay visible in the ledger.")
        ActionItem(Icons.Rounded.Warning, "Risk model", "${summary.anomalies.size} anomaly signal(s) found from duplicates, unusual messages, or security language.")
    }
}

@Composable
private fun AiCopilotPanel(
    summary: FinanceAnalyzer.Summary,
    parsedTransactions: List<Transaction>,
    rejectedSms: List<SmsIntelligence.Review>
) {
    val headline = when {
        rejectedSms.isNotEmpty() -> "Review blocked SMS before trusting the month"
        summary.healthScore < 62 -> "Protect cashflow before adding more spend"
        summary.budgetUtilization > 90 -> "Slow discretionary spend this week"
        parsedTransactions.isNotEmpty() -> "Import the clean queue to update analytics"
        else -> "Scan SMS for a sharper money model"
    }
    val body = when {
        rejectedSms.isNotEmpty() -> "${rejectedSms.size} message(s) look fake, invalid, promotional, OTP, or link-heavy."
        summary.healthScore < 62 -> "Health is ${summary.healthScore}/100 with ${summary.budgetUtilization}% budget use."
        summary.budgetUtilization > 90 -> "Month-end forecast is ${money(summary.forecastedMonthlySpend)} against ${money(summary.plannedSpend)} planned."
        parsedTransactions.isNotEmpty() -> "${parsedTransactions.size} clean transaction(s) are waiting in the import queue."
        else -> "No real SMS queue is active yet; the model is using saved ledger data."
    }
    AppCard(kicker = "Private AI", title = headline) {
        ActionItem(Icons.Rounded.AutoAwesome, "Recommendation", body)
        MetricGrid(
            listOf(
                "Health" to "${summary.healthScore}/100",
                "Blocked SMS" to rejectedSms.size.toString(),
                "Clean queue" to parsedTransactions.size.toString(),
                "Top merchant" to summary.topMerchant
            )
        )
        summary.insights.take(3).forEach { insight ->
            ActionItem(Icons.Rounded.CheckCircle, "Insight", insight)
        }
    }
}

@Composable
private fun AiShieldCompact(rejectedSms: List<SmsIntelligence.Review>, scannedSmsCount: Int) {
    if (scannedSmsCount == 0) return
    AppCard(kicker = "AI shield", title = "${rejectedSms.size} discarded from $scannedSmsCount scanned") {
        val accepted = (scannedSmsCount - rejectedSms.size).coerceAtLeast(0)
        MetricGrid(
            listOf(
                "Accepted" to accepted.toString(),
                "Discarded" to rejectedSms.size.toString()
            )
        )
    }
}

@Composable
private fun SmsShieldPanel(rejectedSms: List<SmsIntelligence.Review>, scannedSmsCount: Int) {
    AppCard(kicker = "AI shield", title = if (rejectedSms.isEmpty()) "No spam discarded" else "Discarded before import") {
        val accepted = (scannedSmsCount - rejectedSms.size).coerceAtLeast(0)
        MetricGrid(
            listOf(
                "Scanned" to scannedSmsCount.toString(),
                "Accepted" to accepted.toString(),
                "Discarded" to rejectedSms.size.toString(),
                "Mode" to "Local"
            )
        )
        if (rejectedSms.isEmpty()) {
            ActionItem(Icons.Rounded.CheckCircle, "Clean queue", "No fake, invalid, OTP, promo, or link-heavy messages were blocked in the latest scan.")
        } else {
            rejectedSms.take(6).forEach { review -> ReviewRow(review) }
            if (rejectedSms.size > 6) Text("+${rejectedSms.size - 6} more blocked", color = Color(0xff64748b), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReviewRow(review: SmsIntelligence.Review) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xfffff7ed), border = BorderStroke(1.dp, Color(0xfffdba74))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${review.label} | ${review.score}", color = Color(0xff9a3412), fontWeight = FontWeight.Black)
                Text(review.action, color = Color(0xff9a3412), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text(review.reason, color = Color(0xff475569), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(review.rawPreview, color = Color(0xff64748b), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun AtlasWorkspace(
    state: FinanceState,
    summary: FinanceAnalyzer.Summary,
    atlasMode: AtlasView.Mode,
    privacyMask: Boolean,
    onAtlasMode: (AtlasView.Mode) -> Unit,
    onPrivacyMask: (Boolean) -> Unit
) {
    VisualAtlasCard(state, summary, atlasMode, privacyMask, onAtlasMode, onPrivacyMask)
    BudgetPressurePanel(state, summary)
    MerchantBars(summary)
}

@Composable
private fun VisualAtlasCard(
    state: FinanceState,
    summary: FinanceAnalyzer.Summary,
    atlasMode: AtlasView.Mode,
    privacyMask: Boolean,
    onAtlasMode: (AtlasView.Mode) -> Unit,
    onPrivacyMask: (Boolean) -> Unit
) {
    AppCard(kicker = "Visual model", title = if (summary.importedSmsCount > 0) "Actual SMS-driven graph" else "Model graph") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModeChip("Flow", atlasMode == AtlasView.Mode.FLOW) { onAtlasMode(AtlasView.Mode.FLOW) }
            ModeChip("Risk", atlasMode == AtlasView.Mode.RISK) { onAtlasMode(AtlasView.Mode.RISK) }
            ModeChip("Merchants", atlasMode == AtlasView.Mode.MERCHANTS) { onAtlasMode(AtlasView.Mode.MERCHANTS) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = privacyMask, onCheckedChange = onPrivacyMask)
            Text("Mask amounts in graph", color = Color(0xff475569), fontWeight = FontWeight.Bold)
        }
        AndroidView(
            factory = { AtlasView(it) },
            update = { it.setData(state, summary, atlasMode, privacyMask) },
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .border(1.dp, Color(0xffcbd5e1), RoundedCornerShape(12.dp))
        )
        MetricGrid(
            listOf(
                "Actual" to money(summary.actualSpend),
                "Plan" to money(summary.plannedSpend),
                "Budget use" to "${summary.budgetUtilization}%",
                "Net" to money(summary.netCashflow)
            )
        )
    }
}

@Composable
private fun BudgetPressurePanel(state: FinanceState, summary: FinanceAnalyzer.Summary) {
    AppCard(kicker = "Category radar", title = "Budget pressure") {
        state.categories.forEach { category ->
            val actual = summary.actualByCategory[category.id] ?: 0.0
            val ratio = if (category.budget > 0) (actual / category.budget).toFloat() else 0f
            ProgressRow(
                label = category.label,
                value = "${money(actual)} / ${money(category.budget)}",
                ratio = ratio,
                color = Color(category.color)
            )
        }
    }
}

@Composable
private fun MerchantBars(summary: FinanceAnalyzer.Summary) {
    val merchants = (if (summary.smsSpendByMerchant.isNotEmpty()) summary.smsSpendByMerchant else summary.spendByMerchant)
        .entries
        .sortedByDescending { it.value }
        .take(6)
    if (merchants.isEmpty()) return
    AppCard(kicker = "Merchant map", title = "Where money is going") {
        val maxValue = max(1.0, merchants.first().value)
        merchants.forEach { entry ->
            val ratio = (entry.value / maxValue).toFloat().coerceIn(0f, 1f)
            ProgressRow(entry.key, money(entry.value), ratio, Color(0xff2563eb))
        }
    }
}

@Composable
private fun CustomizeWorkspace(
    state: FinanceState,
    summary: FinanceAnalyzer.Summary,
    privacyMask: Boolean,
    onPrivacyMask: (Boolean) -> Unit,
    onUpdateProfile: (Double, Double, Double, Double) -> Unit,
    onAddCategory: (String, Double, String, Boolean) -> Unit,
    onUpdateCategory: (CategoryRule, Double, String, Boolean) -> Unit,
    onRemoveCategory: (CategoryRule) -> Unit,
    onAddManual: (String, Double, String, String) -> Unit,
    onClearImported: () -> Unit,
    onReset: () -> Unit
) {
    SecurityCenter(privacyMask, onPrivacyMask, onClearImported, onReset)
    ProfileWorkbench(state.profile, summary, onUpdateProfile)
    CategoryWorkbench(state, summary, onAddCategory, onUpdateCategory, onRemoveCategory)
    LedgerWorkbench(state, onAddManual, onClearImported, onReset)
}

@Composable
private fun SecurityCenter(
    privacyMask: Boolean,
    onPrivacyMask: (Boolean) -> Unit,
    onClearImported: () -> Unit,
    onReset: () -> Unit
) {
    AppCard(kicker = "Security center", title = "Private by default") {
        ActionItem(Icons.Rounded.Lock, "No internet permission", "The Android manifest does not request INTERNET, SEND_SMS, RECEIVE_SMS, contacts, or location.")
        ActionItem(Icons.Rounded.CheckCircle, "Encrypted local vault", "Saved finance state is encrypted with an Android Keystore-backed AES key on this device.")
        ActionItem(Icons.Rounded.Lock, "Screen capture protected", "Sensitive screens are protected with Android FLAG_SECURE to reduce screenshot and recents leakage.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = privacyMask, onCheckedChange = onPrivacyMask)
            Column {
                Text("Mask amounts in charts", color = Color(0xff172033), fontWeight = FontWeight.Black)
                Text("Useful when showing the app to someone else.", color = Color(0xff64748b), fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onClearImported, modifier = Modifier.weight(1f)) {
                Text("Clear SMS")
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun ProfileWorkbench(
    profile: FinanceProfile,
    summary: FinanceAnalyzer.Summary,
    onUpdateProfile: (Double, Double, Double, Double) -> Unit
) {
    var income by remember(profile.monthlyIncome) { mutableStateOf(profile.monthlyIncome.toLong().toString()) }
    var emergency by remember(profile.emergencyFund) { mutableStateOf(profile.emergencyFund.toLong().toString()) }
    var goal by remember(profile.savingsGoal) { mutableStateOf(profile.savingsGoal.toLong().toString()) }
    var target by remember(profile.monthlySaveTarget) { mutableStateOf(profile.monthlySaveTarget.toLong().toString()) }
    AppCard(kicker = "Human profile", title = "Income, runway, targets") {
        MoneyField("Monthly income", income) { income = it }
        MoneyField("Emergency fund", emergency) { emergency = it }
        MoneyField("Savings goal", goal) { goal = it }
        MoneyField("Monthly save target", target) { target = it }
        Button(
            onClick = {
                onUpdateProfile(
                    income.toDoubleOrNull() ?: profile.monthlyIncome,
                    emergency.toDoubleOrNull() ?: profile.emergencyFund,
                    goal.toDoubleOrNull() ?: profile.savingsGoal,
                    target.toDoubleOrNull() ?: profile.monthlySaveTarget
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save profile")
        }
        Text("Current target gap: ${money(summary.freeCash - profile.monthlySaveTarget)}", color = Color(0xff64748b), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryWorkbench(
    state: FinanceState,
    summary: FinanceAnalyzer.Summary,
    onAddCategory: (String, Double, String, Boolean) -> Unit,
    onUpdateCategory: (CategoryRule, Double, String, Boolean) -> Unit,
    onRemoveCategory: (CategoryRule) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var essential by remember { mutableStateOf(true) }
    AppCard(kicker = "Rules", title = "Categories and matching") {
        OutlinedTextField(label, { label = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                budget,
                { budget = it },
                label = { Text("Budget") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Checkbox(checked = essential, onCheckedChange = { essential = it })
                Text("Essential")
            }
        }
        OutlinedTextField(keywords, { keywords = it }, label = { Text("Keywords") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onAddCategory(label, budget.toDoubleOrNull() ?: 0.0, keywords, essential) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add rule")
        }
        state.categories.forEach { category ->
            CategoryEditor(category, summary.actualByCategory[category.id] ?: 0.0, onUpdateCategory, onRemoveCategory)
        }
    }
}

@Composable
private fun CategoryEditor(
    category: CategoryRule,
    actual: Double,
    onUpdateCategory: (CategoryRule, Double, String, Boolean) -> Unit,
    onRemoveCategory: (CategoryRule) -> Unit
) {
    var budget by remember(category.id) { mutableStateOf(category.budget.toLong().toString()) }
    var keywords by remember(category.id) { mutableStateOf(category.keywords.joinToString(", ")) }
    var essential by remember(category.id) { mutableStateOf(category.essential) }
    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xfff8fafc), border = BorderStroke(1.dp, Color(0xffe2e8f0))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(category.label, color = Color(category.color), fontWeight = FontWeight.Black)
                Text("${money(actual)} used", color = Color(0xff64748b), fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(budget, { budget = it }, label = { Text("Budget") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(keywords, { keywords = it }, label = { Text("Rules") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = essential, onCheckedChange = { essential = it })
                Text("Essential")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onUpdateCategory(category, budget.toDoubleOrNull() ?: 0.0, keywords, essential) }, modifier = Modifier.weight(1f)) {
                    Text("Save")
                }
                OutlinedButton(onClick = { onRemoveCategory(category) }, modifier = Modifier.weight(1f)) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun LedgerWorkbench(
    state: FinanceState,
    onAddManual: (String, Double, String, String) -> Unit,
    onClearImported: () -> Unit,
    onReset: () -> Unit
) {
    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("debit") }
    var category by remember(state.categories.size) { mutableStateOf(state.categories.firstOrNull()?.label ?: "Shopping") }
    AppCard(kicker = "Ledger", title = "Transactions used by analytics") {
        OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant/source") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModeChip("Debit", type == "debit") { type = "debit" }
            ModeChip("Credit", type == "credit") { type = "credit" }
        }
        OutlinedTextField(category, { category = it }, label = { Text("Category label") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onAddManual(merchant, amount.toDoubleOrNull() ?: 0.0, type, category) }, modifier = Modifier.fillMaxWidth()) {
            Text("Add transaction")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onClearImported, modifier = Modifier.weight(1f)) {
                Text("Clear SMS")
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                Text("Reset")
            }
        }
        state.transactions.take(12).forEach { TransactionPreview(it) }
    }
}

@Composable
private fun AppCard(kicker: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xffffffff),
        border = BorderStroke(1.dp, Color(0xffdce3ec)),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(kicker.uppercase(Locale.ROOT), color = Color(0xff05603a), fontWeight = FontWeight.Black, fontSize = 12.sp)
            Text(title, color = Color(0xff172033), fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 28.sp)
            content()
        }
    }
}

@Composable
private fun HealthGauge(score: Int, label: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(156.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(148.dp)) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            val arcSize = Size(size.width - 18.dp.toPx(), size.height - 18.dp.toPx())
            val topLeft = Offset(9.dp.toPx(), 9.dp.toPx())
            drawArc(Color(0xffdbe4ee), startAngle = 140f, sweepAngle = 260f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            drawArc(healthColor(score), startAngle = 140f, sweepAngle = 260f * (score / 100f), useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color(0xff172033))
            Text(label, color = Color(0xff64748b), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BigNumber(label: String, value: String, positive: Boolean) {
    Surface(shape = RoundedCornerShape(14.dp), color = if (positive) Color(0xffecfdf5) else Color(0xfffff1f2), border = BorderStroke(1.dp, if (positive) Color(0xff86efac) else Color(0xfffecdd3))) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(label, color = Color(0xff64748b), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(value, color = if (positive) Color(0xff047857) else Color(0xffbe123c), fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MetricGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    MetricTile(label, value, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = Color(0xfff8fafc), border = BorderStroke(1.dp, Color(0xffe2e8f0))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Color(0xff64748b), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = Color(0xff172033), fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionItem(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xfff8fafc), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(shape = CircleShape, color = Color(0xffe0f2fe), modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xff2563eb), modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color(0xff172033), fontWeight = FontWeight.Black)
            Text(body, color = Color(0xff475569), lineHeight = 18.sp, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProgressRow(label: String, value: String, ratio: Float, color: Color) {
    val safeRatio = ratio.coerceIn(0f, 1.25f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xff172033), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(value, color = Color(0xff64748b), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
            drawRoundRect(Color(0xffe2e8f0), size = Size(size.width, size.height))
            drawRoundRect(if (ratio > 1f) Color(0xffbe123c) else color, size = Size(size.width * safeRatio.coerceAtMost(1f), size.height))
        }
    }
}

@Composable
private fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun TransactionPreview(transaction: Transaction) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xfff8fafc), border = BorderStroke(1.dp, Color(0xffe2e8f0))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${if (transaction.isCredit) "+" else "-"}${money(transaction.amount)}  ${transaction.name}",
                color = if (transaction.isCredit) Color(0xff059669) else Color(0xffbe123c),
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("${transaction.date} | ${transaction.category} | ${transaction.source} | ${transaction.confidence}% signal", color = Color(0xff64748b), fontSize = 12.sp)
            if (!transaction.raw.isNullOrBlank()) Text(transaction.raw.take(160), color = Color(0xff64748b), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun WarningPanel(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xfffff7ed), border = BorderStroke(1.dp, Color(0xfffdba74))) {
        Text(text, color = Color(0xff9a3412), fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp), lineHeight = 18.sp)
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontWeight = FontWeight.Bold) })
}

private fun tabIcon(tab: CashflowTab): ImageVector =
    when (tab) {
        CashflowTab.TODAY -> Icons.Rounded.Home
        CashflowTab.SMS -> Icons.Rounded.Email
        CashflowTab.ATLAS -> Icons.Rounded.PieChart
        CashflowTab.CUSTOMIZE -> Icons.Rounded.Settings
    }

private fun healthColor(score: Int): Color =
    when {
        score >= 82 -> Color(0xff059669)
        score >= 62 -> Color(0xffea580c)
        else -> Color(0xffbe123c)
    }

private fun money(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply {
        maximumFractionDigits = 0
    }.format(value)
