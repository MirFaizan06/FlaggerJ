package com.example.flaggerdemo;

import com.flaggerj.core.annotation.FeatureContainer;
import com.flaggerj.core.annotation.FeatureFlag;
import com.flaggerj.core.context.FeatureContext;

/**
 * Example flag container. This file is not compiled as part of the FlaggerJ build — it is a
 * reference you can copy into your own project. See ../../../../../../../../README.md for the
 * full walkthrough.
 */
@FeatureContainer
public interface AppFeatures {

    @FeatureFlag(key = "new-checkout", defaultValue = "false")
    boolean isNewCheckoutEnabled(FeatureContext context);

    @FeatureFlag(key = "max-items-per-cart", defaultValue = "10")
    int getMaxItemsPerCart(FeatureContext context);

    @FeatureFlag(key = "checkout-discount-rate", defaultValue = "0.0")
    double getCheckoutDiscountRate(FeatureContext context);

    @FeatureFlag(key = "welcome-message", defaultValue = "Welcome!")
    String getWelcomeMessage(FeatureContext context);
}
