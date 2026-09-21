package com.jojokorok.nourishrx.premium;

import android.app.Activity;
import android.content.Context;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Maintains the app's single connection to Google Play Billing. */
public final class PlayBillingClient implements PurchasesUpdatedListener, AutoCloseable {
    public enum ConnectionState {
        IDLE,
        CONNECTING,
        READY,
        DISCONNECTED,
        UNAVAILABLE,
        CLOSED
    }

    public interface Listener {
        void onConnectionStateChanged(ConnectionState state, BillingResult result);

        void onPremiumProductDetails(BillingResult result, ProductDetails productDetails);

        void onPremiumPurchasesQueried(BillingResult result, List<Purchase> purchases);

        void onPremiumPurchaseAcknowledged(BillingResult result, String purchaseToken);

        void onPurchasesUpdated(BillingResult result, List<Purchase> purchases);
    }

    private final BillingClient billingClient;
    private final Listener listener;
    private final AtomicBoolean connectionInProgress = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public PlayBillingClient(Context context, Listener listener) {
        this.listener = listener;
        billingClient = BillingClient.newBuilder(context.getApplicationContext())
                .setListener(this)
                .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder()
                                .enableOneTimeProducts()
                                .build()
                )
                .enableAutoServiceReconnection()
                .build();
    }

    public void connect() {
        if (closed.get()) {
            listener.onConnectionStateChanged(ConnectionState.CLOSED, null);
            return;
        }
        if (billingClient.isReady()) {
            listener.onConnectionStateChanged(ConnectionState.READY, null);
            queryPremiumProduct();
            queryPremiumPurchases();
            return;
        }
        if (!connectionInProgress.compareAndSet(false, true)) {
            return;
        }

        listener.onConnectionStateChanged(ConnectionState.CONNECTING, null);
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                connectionInProgress.set(false);
                if (closed.get()) {
                    return;
                }
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    listener.onConnectionStateChanged(ConnectionState.READY, result);
                    queryPremiumProduct();
                    queryPremiumPurchases();
                } else {
                    listener.onConnectionStateChanged(ConnectionState.UNAVAILABLE, result);
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connectionInProgress.set(false);
                if (!closed.get()) {
                    listener.onConnectionStateChanged(ConnectionState.DISCONNECTED, null);
                }
            }
        });
    }

    @Override
    public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        if (closed.get()) {
            return;
        }
        List<Purchase> snapshot = purchases == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(purchases));
        listener.onPurchasesUpdated(result, snapshot);
    }

    public void refreshPurchases() {
        if (closed.get()) {
            return;
        }
        if (!billingClient.isReady()) {
            connect();
            return;
        }
        queryPremiumPurchases();
    }

    public boolean acknowledgePremiumPurchase(String purchaseToken) {
        if (closed.get() || !billingClient.isReady()) {
            return false;
        }
        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build();
        billingClient.acknowledgePurchase(params, result -> {
            if (!closed.get()) {
                listener.onPremiumPurchaseAcknowledged(result, purchaseToken);
            }
        });
        return true;
    }

    public BillingResult launchPremiumPurchase(
            Activity activity,
            ProductDetails productDetails,
            String offerToken
    ) {
        if (closed.get() || !billingClient.isReady()) {
            return BillingResult.newBuilder()
                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                    .setDebugMessage("Google Play Billing is not connected")
                    .build();
        }
        BillingFlowParams.ProductDetailsParams.Builder productParams =
                BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails);
        if (offerToken != null && !offerToken.isEmpty()) {
            productParams.setOfferToken(offerToken);
        }
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                        Collections.singletonList(productParams.build())
                )
                .build();
        return billingClient.launchBillingFlow(activity, params);
    }

    private void queryPremiumProduct() {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PremiumManager.PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(product))
                .build();

        billingClient.queryProductDetailsAsync(params, this::handleProductDetails);
    }

    private void handleProductDetails(
            BillingResult result,
            QueryProductDetailsResult detailsResult
    ) {
        if (closed.get()) {
            return;
        }
        ProductDetails premiumProduct = null;
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            for (ProductDetails candidate : detailsResult.getProductDetailsList()) {
                if (PremiumManager.PREMIUM_PRODUCT_ID.equals(candidate.getProductId())) {
                    premiumProduct = candidate;
                    break;
                }
            }
        }
        listener.onPremiumProductDetails(result, premiumProduct);
    }

    private void queryPremiumPurchases() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        billingClient.queryPurchasesAsync(params, (result, purchases) -> {
            if (closed.get()) {
                return;
            }
            List<Purchase> snapshot = Collections.unmodifiableList(new ArrayList<>(purchases));
            listener.onPremiumPurchasesQueried(result, snapshot);
        });
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        connectionInProgress.set(false);
        billingClient.endConnection();
        listener.onConnectionStateChanged(ConnectionState.CLOSED, null);
    }
}
