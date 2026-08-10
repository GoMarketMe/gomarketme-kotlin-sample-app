package co.gomarketme.kotlinsampleapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import co.gomarketme.kotlin.GoMarketMe
import co.gomarketme.kotlin.GoMarketMeAffiliateMarketingData
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), PurchasesUpdatedListener {
    private lateinit var billingClient: BillingClient
    private val goMarketMeSDK = GoMarketMe

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize GoMarketMe SDK.
        // Replace API_KEY with your actual GoMarketMe API key.
        goMarketMeSDK.initialize(this, "API_KEY")

        enableEdgeToEdge()

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
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Toast.makeText(
                        this@MainActivity,
                        "Billing Client Ready",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${billingResult.debugMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onBillingServiceDisconnected() {
                Toast.makeText(
                    this@MainActivity,
                    "Billing Client Disconnected",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        setContent {
            KotlinSampleAppTheme {
                val affiliateData = remember {
                    mutableStateOf<GoMarketMeAffiliateMarketingData?>(null)
                }

                LaunchedEffect(Unit) {
                    // GoMarketMe.initialize(...) runs asynchronously.
                    // For this sample app, wait briefly before reading the data.
                    delay(3000)
                    affiliateData.value = GoMarketMe.affiliateMarketingData
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(
                        modifier = Modifier.padding(innerPadding),
                        affiliateData = affiliateData,
                        onBuyButtonClick = { initiatePurchase("productid4") }
                    )
                }
            }
        }
    }

    private fun initiatePurchase(productId: String) {
        if (!billingClient.isReady) {
            Toast.makeText(this, "Billing Client is not ready", Toast.LENGTH_SHORT).show()
            return
        }

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

        billingClient.queryProductDetailsAsync(productDetailsParams) { billingResult, queryProductDetailsResult ->
            val productDetailsList = queryProductDetailsResult.productDetailsList

            if (
                billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                productDetailsList.isNotEmpty()
            ) {
                val productDetails = productDetailsList.first()

                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                    .build()

                billingClient.launchBillingFlow(this, billingFlowParams)
            } else {
                Toast.makeText(
                    this,
                    "Product not found or error: ${billingResult.debugMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { purchase ->
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(this, "Purchase canceled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Error: ${billingResult.debugMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            lifecycleScope.launch {
                try {
                    goMarketMeSDK.syncAllTransactions()
                    consumePurchase(purchase)
                } catch (throwable: Throwable) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to sync purchase: ${throwable.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Toast.makeText(this, "Purchase consumed. You can buy it again.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Failed to consume purchase: ${billingResult.debugMessage}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    affiliateData: MutableState<GoMarketMeAffiliateMarketingData?>,
    onBuyButtonClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Greeting(name = "Android")

        Spacer(modifier = Modifier.height(16.dp))

        val data = affiliateData.value

        if (data != null) {
            Text(text = "Affiliate ID: ${data.affiliate.id}")
            Text(text = "Affiliate %: ${data.saleDistribution.affiliatePercentage}")
            Text(text = "Campaign ID: ${data.campaign.id}")
        } else {
            Text(text = "No affiliate marketing data found yet")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onBuyButtonClick) {
            Text(text = "Buy Product")
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello GoMarketMe SDK v.5.0.2!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    KotlinSampleAppTheme {
        MainContent(
            affiliateData = remember {
                mutableStateOf(null)
            },
            onBuyButtonClick = {}
        )
    }
}