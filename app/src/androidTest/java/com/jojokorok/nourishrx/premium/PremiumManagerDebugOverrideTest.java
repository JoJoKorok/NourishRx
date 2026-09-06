package com.jojokorok.nourishrx.premium;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class PremiumManagerDebugOverrideTest {
    private IsolatedPreferencesContext context;

    @Before
    public void setUp() {
        context = new IsolatedPreferencesContext(
                ApplicationProvider.getApplicationContext(),
                "premium_test_" + System.nanoTime() + "_"
        );
    }

    @Test
    public void debugOverrideUnlocksAndRelocksPremiumFeatures() {
        PremiumManager manager = new PremiumManager(context, true);

        assertFalse(manager.isPremiumActive());
        manager.setDebugPremiumOverride(true);
        assertTrue(manager.isDebugPremiumOverrideActive());
        assertTrue(manager.canUse(PremiumFeature.DATA_IMPORT_EXPORT));

        manager.setDebugPremiumOverride(false);
        assertFalse(manager.isPremiumActive());
    }

    @Test
    public void releaseConfigurationIgnoresStoredDebugOverride() {
        PremiumManager debugManager = new PremiumManager(context, true);
        debugManager.setDebugPremiumOverride(true);

        PremiumManager releaseManager = new PremiumManager(context, false);

        assertFalse(releaseManager.isDebugPremiumOverrideAllowed());
        assertFalse(releaseManager.isDebugPremiumOverrideActive());
        assertFalse(releaseManager.isPremiumActive());
    }

    @Test
    public void disablingDebugOverrideDoesNotClearVerifiedEntitlement() {
        PremiumManager manager = new PremiumManager(context, true);
        manager.cachePremiumEntitlement(true, 1_752_000_000_000L);
        manager.setDebugPremiumOverride(true);

        manager.setDebugPremiumOverride(false);

        assertFalse(manager.isDebugPremiumOverrideActive());
        assertTrue(manager.isPremiumActive());
        assertTrue(manager.premiumVerifiedAt() > 0);
    }

    private static final class IsolatedPreferencesContext extends ContextWrapper {
        private final String prefix;

        IsolatedPreferencesContext(Context base, String prefix) {
            super(base);
            this.prefix = prefix;
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return super.getSharedPreferences(prefix + name, mode);
        }
    }
}
