package com.jojokorok.nourishrx.premium;

import android.content.Context;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryProductDetailsResult;

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
