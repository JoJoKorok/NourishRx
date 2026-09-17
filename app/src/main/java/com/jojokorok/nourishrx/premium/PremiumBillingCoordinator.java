package com.jojokorok.nourishrx.premium;

import android.content.Context;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reconciles locally cached Premium access with purchases reported by Google Play. */
public final class PremiumBillingCoordinator implements PlayBillingClient.Listener, AutoCloseable {
    private final PremiumManager premiumManager;
    private final Runnable stateChanged;
    private final PlayBillingClient billingClient;
    private final Set<String> acknowledgementsInFlight = new HashSet<>();
    private PremiumOfferState premiumOffer = PremiumOfferState.checking();

    public PremiumBillingCoordinator(
            Context context,
            PremiumManager premiumManager,
            Runnable stateChanged
    ) {
        this.premiumManager = premiumManager;
        this.stateChanged = stateChanged;
        billingClient = new PlayBillingClient(context, this);
    }

    public void start() {
        billingClient.connect();
    }

    public void refresh() {
        billingClient.refreshPurchases();
    }

    public PremiumOfferState premiumOffer() {
        return premiumOffer;
    }

    @Override
    public void onConnectionStateChanged(
            PlayBillingClient.ConnectionState state,
            BillingResult result
    ) {
        if (state == PlayBillingClient.ConnectionState.DISCONNECTED
                || state == PlayBillingClient.ConnectionState.UNAVAILABLE) {
            acknowledgementsInFlight.clear();
        }
        if (state == PlayBillingClient.ConnectionState.CONNECTING
                || state == PlayBillingClient.ConnectionState.DISCONNECTED) {
            updatePremiumOffer(PremiumOfferState.checking());
        } else if (state == PlayBillingClient.ConnectionState.UNAVAILABLE) {
            updatePremiumOffer(PremiumOfferState.unavailable());
        }
    }

    @Override
    public void onPremiumProductDetails(BillingResult result, ProductDetails productDetails) {
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK
                || productDetails == null
                || productDetails.getOneTimePurchaseOfferDetailsList().isEmpty()) {
            updatePremiumOffer(PremiumOfferState.unavailable());
            return;
        }
        String formattedPrice = productDetails.getOneTimePurchaseOfferDetailsList()
                .get(0)
                .getFormattedPrice();
        updatePremiumOffer(PremiumOfferState.available(formattedPrice));
    }

    @Override
    public void onPremiumPurchasesQueried(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            return;
        }

        boolean wasPremium = premiumManager.isPremiumActive();
        boolean premiumOwned = false;
        boolean acknowledgementPending = false;
        for (Purchase purchase : purchases) {
            if (PremiumPurchaseEvaluator.grantsPremium(
                    purchase.getProducts(),
                    purchase.getPurchaseState(),
                    purchase.isAcknowledged()
            )) {
                premiumOwned = true;
            } else if (PremiumPurchaseEvaluator.requiresAcknowledgement(
                    purchase.getProducts(),
                    purchase.getPurchaseState(),
                    purchase.isAcknowledged()
            )) {
                acknowledgementPending = true;
                acknowledge(purchase);
            }
        }

        if (premiumOwned || !acknowledgementPending) {
            premiumManager.cachePremiumEntitlement(premiumOwned, System.currentTimeMillis());
            if (wasPremium != premiumManager.isPremiumActive()) {
                stateChanged.run();
            }
        }
    }

    @Override
    public void onPremiumPurchaseAcknowledged(BillingResult result, String purchaseToken) {
        acknowledgementsInFlight.remove(purchaseToken);
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            billingClient.refreshPurchases();
        }
    }

    @Override
    public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            billingClient.refreshPurchases();
        }
    }

    @Override
    public void close() {
        acknowledgementsInFlight.clear();
        billingClient.close();
    }

    private void acknowledge(Purchase purchase) {
        String purchaseToken = purchase.getPurchaseToken();
        if (acknowledgementsInFlight.add(purchaseToken)) {
            boolean started = billingClient.acknowledgePremiumPurchase(purchaseToken);
            if (!started) {
                acknowledgementsInFlight.remove(purchaseToken);
            }
        }
    }

    private void updatePremiumOffer(PremiumOfferState updatedOffer) {
        if (!updatedOffer.equals(premiumOffer)) {
            premiumOffer = updatedOffer;
            stateChanged.run();
        }
    }
}
