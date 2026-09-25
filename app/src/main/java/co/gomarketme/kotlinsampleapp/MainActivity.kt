package co.gomarketme.kotlinsampleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import co.gomarketme.kotlin.GoMarketMe
import co.gomarketme.kotlin.GoMarketMeAffiliateMarketingData
import co.gomarketme.kotlin.GoMarketMeReferralCodeTrigger
import co.gomarketme.kotlinsampleapp.ui.theme.KotlinSampleAppTheme
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), PurchasesUpdatedListener {
    private lateinit var billingClient: BillingClient
    private val goMarketMe = GoMarketMe

    private var affiliateData by mutableStateOf<GoMarketMeAffiliateMarketingData?>(null)
    private var sdkReady by mutableStateOf(false)
    private var sdkError by mutableStateOf<String?>(null)
    private var billingReady by mutableStateOf(false)
    private var isSyncing by mutableStateOf(false)
    private var isPurchasing by mutableStateOf(false)
    private var syncMessage by mutableStateOf<SampleMessage?>(null)
    private var purchaseMessage by mutableStateOf<SampleMessage?>(null)
    private var referralMessage by mutableStateOf<SampleMessage?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Required: initialize once when the app starts.
        goMarketMe.initialize(this, "API_KEY")
        observeGoMarketMeInitialization()
        initializeBilling()

        setContent {
            KotlinSampleAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(
                        modifier = Modifier.padding(innerPadding),
                        affiliateData = affiliateData,
                        sdkReady = sdkReady,
                        sdkError = sdkError,
                        billingReady = billingReady,
                        isSyncing = isSyncing,
                        isPurchasing = isPurchasing,
                        syncMessage = syncMessage,
                        purchaseMessage = purchaseMessage,
                        referralMessage = referralMessage,
                        onReferralResult = { data ->
                            if (data != null) {
                                affiliateData = data
                                referralMessage = data.referralCode.nonEmpty()?.let {
                                    SampleMessage.success("Referral code $it applied.")
                                } ?: SampleMessage.info(
                                    "This device is already attributed through an affiliate link."
                                )
                            }
                        },
                        onReferralError = { error ->
                            referralMessage = SampleMessage.error(
                                error.message ?: "Could not open referral codes."
                            )
                        },
                        onSyncClick = ::syncCurrentPurchases,
                        onBuyButtonClick = { initiatePurchase(TEST_PRODUCT_ID) }
                    )
                }
            }
        }
    }

    private fun observeGoMarketMeInitialization() {
        lifecycleScope.launch {
            try {
                // This call waits for SDK initialization. The trigger uses the same settings.
                goMarketMe.referralCodeSettings()
                affiliateData = goMarketMe.affiliateMarketingData
                sdkReady = true
            } catch (error: Throwable) {
                sdkError = error.message ?: "GoMarketMe could not initialize."
            }
        }
    }

    private fun initializeBilling() {
        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                billingReady = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                if (!billingReady) {
                    purchaseMessage = SampleMessage.error(billingResult.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                billingReady = false
                purchaseMessage = SampleMessage.info("Google Play Billing disconnected.")
            }
        })
    }

    private fun syncCurrentPurchases() {
        if (!sdkReady || isSyncing) return

        isSyncing = true
        syncMessage = null
        lifecycleScope.launch {
            try {
                val result = goMarketMe.syncAllTransactions()
                syncMessage = if (result.success) {
                    SampleMessage.success(
                        "Synced ${result.sentCount} of ${result.fetchedCount} transaction(s)."
                    )
                } else {
                    SampleMessage.error(
                        "Sync did not complete. ${result.failedCount} transaction(s) failed."
                    )
                }
            } catch (error: Throwable) {
                syncMessage = SampleMessage.error(
                    error.message ?: "Purchase sync failed."
                )
            } finally {
                isSyncing = false
            }
        }
    }

    private fun initiatePurchase(productId: String) {
        if (!billingClient.isReady) {
            purchaseMessage = SampleMessage.info("Google Play Billing is not ready.")
            return
        }

        isPurchasing = true
        purchaseMessage = null

        val productDetailsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(productDetailsParams) { result, response ->
            val products = response.productDetailsList
            if (result.responseCode == BillingClient.BillingResponseCode.OK && products.isNotEmpty()) {
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(products.first())
                                .build()
                        )
                    )
                    .build()
                billingClient.launchBillingFlow(this, flowParams)
            } else {
                isPurchasing = false
                purchaseMessage = SampleMessage.error(
                    "Test product unavailable: ${result.debugMessage}"
                )
            }
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach(::handlePurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                isPurchasing = false
                purchaseMessage = SampleMessage.info("Purchase cancelled.")
            }
            else -> {
                isPurchasing = false
                purchaseMessage = SampleMessage.error(billingResult.debugMessage)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            purchaseMessage = SampleMessage.info("Purchase pending approval.")
            return
        }

        lifecycleScope.launch {
            val synced = try {
                goMarketMe.syncAllTransactions().success
            } catch (error: Throwable) {
                false
            }
            consumePurchase(purchase, synced)
        }
    }

    private fun consumePurchase(purchase: Purchase, synced: Boolean) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(params) { result, _ ->
            isPurchasing = false
            purchaseMessage = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                if (synced) {
                    SampleMessage.success("Purchase completed and synced.")
                } else {
                    SampleMessage.error(
                        "Purchase completed, but GoMarketMe sync needs attention."
                    )
                }
            } else {
                SampleMessage.error("Could not consume purchase: ${result.debugMessage}")
            }
        }
    }

    companion object {
        private const val TEST_PRODUCT_ID = "productid4"
    }
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    affiliateData: GoMarketMeAffiliateMarketingData?,
    sdkReady: Boolean,
    sdkError: String?,
    billingReady: Boolean,
    isSyncing: Boolean,
    isPurchasing: Boolean,
    syncMessage: SampleMessage?,
    purchaseMessage: SampleMessage?,
    referralMessage: SampleMessage?,
    onReferralResult: (GoMarketMeAffiliateMarketingData?) -> Unit,
    onReferralError: (Throwable) -> Unit,
    onSyncClick: () -> Unit,
    onBuyButtonClick: () -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 20.dp,
            bottom = 24.dp
        )
    ) {
        item { SampleHeader() }

        item {
            SampleSection(
                badge = "Required",
                title = "Initialize",
                description = "Initialize once when your app starts. Affiliate-link attribution is handled automatically."
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!sdkReady && sdkError == null) {
                        CircularProgressIndicator(modifier = Modifier.width(22.dp))
                    } else {
                        Text(
                            text = if (sdkReady) "✓" else "!",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (sdkReady) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = when {
                                sdkReady -> "SDK ready"
                                sdkError != null -> "Initialization failed"
                                else -> "Initializing GoMarketMe…"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when {
                                sdkError != null -> sdkError
                                affiliateData != null -> "Ready · attribution loaded"
                                sdkReady -> "Ready · no existing attribution"
                                else -> "Calling GoMarketMe.initialize(context, apiKey)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            SampleSection(
                badge = "Optional",
                title = "Referral codes",
                description = "Referral codes are the fallback when an affiliate link is not practical. Place this UI on the first screen users see after installing the app."
            ) {
                GoMarketMeReferralCodeTrigger(
                    modifier = Modifier.fillMaxWidth(),
                    onResult = onReferralResult,
                    onError = onReferralError
                )
                referralMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    MessageView(it)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The trigger text, colors, typography, and layout are configured in GoMarketMe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SampleSection(
                badge = "Recommended",
                title = "Report purchases",
                description = "GoMarketMe detects and reports purchases automatically. We also recommend manually syncing after your purchase provider confirms a successful transaction."
            ) {
                Button(
                    onClick = onSyncClick,
                    enabled = sdkReady && !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSyncing) "Syncing…" else "Manually sync purchases")
                }
                syncMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    MessageView(it)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = "Google Play Billing test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Uses sample product productid4. After purchase, the sample syncs with GoMarketMe before consuming it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onBuyButtonClick,
                    enabled = billingReady && !isPurchasing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isPurchasing) "Purchasing…" else "Buy test product")
                }
                purchaseMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    MessageView(it)
                }
            }
        }

        item {
            SampleSection(
                badge = "Optional",
                title = "Programmatic affiliate data",
                description = "Use the initialization response to personalize onboarding, paywalls, offers, or other app content."
            ) {
                if (affiliateData == null) {
                    MessageView(
                        SampleMessage.info(
                            "No attribution is active. Referral codes remain available as a fallback."
                        )
                    )
                } else {
                    AffiliateDataView(affiliateData)
                }
            }
        }
    }
}

@Composable
private fun SampleHeader() {
    Column {
        Text(
            text = "GoMarketMe Kotlin SDK",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Sample integration · SDK 6.0.0",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SampleSection(
    badge: String,
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun AffiliateDataView(data: GoMarketMeAffiliateMarketingData) {
    val referralCode = data.referralCode.nonEmpty()
    Column {
        KeyValueRow(
            "Attribution",
            referralCode?.let { "Referral code ($it)" } ?: "Affiliate link"
        )
        KeyValueRow("Affiliate ID", data.affiliate.id)
        KeyValueRow("Campaign ID", data.campaign.id)
        KeyValueRow(
            "Affiliate share",
            data.saleDistribution.affiliatePercentage.nonEmpty()?.let { "$it%" } ?: "—"
        )
        KeyValueRow("Referral code", referralCode ?: "—")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This device is attributed. A referral code cannot replace the existing attribution.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value.ifBlank { "—" },
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            fontFamily = FontFamily.Monospace
        )
    }
}

enum class MessageKind { Info, Success, Error }

data class SampleMessage(val kind: MessageKind, val text: String) {
    companion object {
        fun info(text: String) = SampleMessage(MessageKind.Info, text)
        fun success(text: String) = SampleMessage(MessageKind.Success, text)
        fun error(text: String) = SampleMessage(MessageKind.Error, text)
    }
}

@Composable
private fun MessageView(message: SampleMessage) {
    val color = when (message.kind) {
        MessageKind.Info -> MaterialTheme.colorScheme.primary
        MessageKind.Success -> MaterialTheme.colorScheme.tertiary
        MessageKind.Error -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = message.text,
            modifier = Modifier.padding(10.dp),
            color = color,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun String?.nonEmpty(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    KotlinSampleAppTheme {
        MainContent(
            affiliateData = null,
            sdkReady = true,
            sdkError = null,
            billingReady = true,
            isSyncing = false,
            isPurchasing = false,
            syncMessage = null,
            purchaseMessage = null,
            referralMessage = null,
            onReferralResult = {},
            onReferralError = {},
            onSyncClick = {},
            onBuyButtonClick = {}
        )
    }
}
