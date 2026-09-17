package com.jojokorok.nourishrx.premium;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PremiumOfferStateTest {
    @Test
    public void matchingCheckingStatesAreEqual() {
        assertEquals(PremiumOfferState.checking(), PremiumOfferState.checking());
    }

    @Test
    public void availableOfferExposesLocalizedPrice() {
        PremiumOfferState offer = PremiumOfferState.available("$4.99");

        assertEquals(PremiumOfferState.Status.AVAILABLE, offer.getStatus());
        assertEquals("$4.99", offer.getFormattedPrice());
    }

    @Test
    public void priceChangeProducesDifferentSnapshot() {
        assertNotEquals(
                PremiumOfferState.available("$4.99"),
                PremiumOfferState.available("$5.99")
        );
    }
}
