package com.example.iac;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

/**
 * @author Luiz Decaro
 */
public class RedriveLambdaApp {
    public static void main(final String[] args) {
        App app = new App();

        new RedriveLambdaStack(app, "RedriveLambda", StackProps.builder()
                .build());

        app.synth();
    }
}

