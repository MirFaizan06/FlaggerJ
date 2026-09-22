package com.example.flaggerdemo;

import com.flaggerj.core.client.FlaggerClient;
import com.flaggerj.core.context.FeatureContext;

import java.nio.file.Path;

/**
 * Example entry point showing the two supported ways of configuring flags: loading them from a
 * local file, and registering them programmatically. Not compiled as part of the FlaggerJ build.
 */
public final class Main {

    public static void main(String[] args) {
        FlaggerClient client = FlaggerClient.create();

        // Load base configuration from a local JSON/YAML file (see flags.yaml next to this file).
        client.loadFromFile(Path.of("examples/src/main/resources/flags.yaml"));

        // The generated, reflection-free implementation of AppFeatures.
        AppFeatures features = new AppFeaturesImpl(client);

        FeatureContext usCustomer = FeatureContext.builder()
                .userId("user-42")
                .country("US")
                .build();

        FeatureContext deCustomer = FeatureContext.builder()
                .userId("user-99")
                .country("DE")
                .build();

        System.out.println("US customer sees new checkout: " + features.isNewCheckoutEnabled(usCustomer));
        System.out.println("DE customer sees new checkout: " + features.isNewCheckoutEnabled(deCustomer));
        System.out.println("Max items per cart: " + features.getMaxItemsPerCart(usCustomer));
        System.out.println("Welcome message: " + features.getWelcomeMessage(usCustomer));
    }
}
