package com.jojokorok.nourishrx.premium;

import android.app.Activity;
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
    private ProductDetails premiumProductDetails;
    private String premiumOfferToken = "";

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

    public PremiumPurchaseLaunchResult launchPremiumPurchase(Activity activity) {
        if (premiumManager.isPremiumActive()) {
            return PremiumPurchaseLaunchResult.ALREADY_OWNED;
        }
        if (premiumOffer.getStatus() != PremiumOfferState.Status.AVAILABLE
                || premiumProductDetails == null) {
            return PremiumPurchaseLaunchResult.OFFER_UNAVAILABLE;
        }
        BillingResult result = billingClient.launchPremiumPurchase(
                activity,
                premiumProductDetails,
                premiumOfferToken
        );
        PremiumPurchaseLaunchResult launchResult =
                PremiumPurchaseLaunchResult.fromBillingResponse(result.getResponseCode());
        if (launchResult == PremiumPurchaseLaunchResult.ALREADY_OWNED) {
            billingClient.refreshPurchases();
        } else if (launchResult == PremiumPurchaseLaunchResult.OFFER_UNAVAILABLE) {
            clearPremiumProduct();
            updatePremiumOffer(PremiumOfferState.unavailable());
        }
        return launchResult;
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
            clearPremiumProduct();
            updatePremiumOffer(PremiumOfferState.checking());
        } else if (state == PlayBillingClient.ConnectionState.UNAVAILABLE) {
            clearPremiumProduct();
            updatePremiumOffer(PremiumOfferState.unavailable());
        }
    }

    @Override
    public void onPremiumProductDetails(BillingResult result, ProductDetails productDetails) {
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK
                || productDetails == null
                || productDetails.getOneTimePurchaseOfferDetailsList().isEmpty()) {
            clearPremiumProduct();
            updatePremiumOffer(PremiumOfferState.unavailable());
            return;
        }
        ProductDetails.OneTimePurchaseOfferDetails offer =
                productDetails.getOneTimePurchaseOfferDetailsList().get(0);
        premiumProductDetails = productDetails;
        premiumOfferToken = offer.getOfferToken();
        updatePremiumOffer(PremiumOfferState.available(offer.getFormattedPrice()));
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

    private void clearPremiumProduct() {
        premiumProductDetails = null;
        premiumOfferToken = "";
    }
}
