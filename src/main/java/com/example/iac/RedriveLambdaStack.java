package com.example.iac;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.ILayerVersion;
import software.amazon.awscdk.services.lambda.LayerVersion;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Tracing;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.IBucket;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

public class RedriveLambdaStack extends Stack {
    
    public RedriveLambdaStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public RedriveLambdaStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        String s3BucketName = "test-bucket-redrive";
        // The code that defines your stack goes here

        // example resource
        final Queue queue = Queue.Builder.create(this, "RedriveLambdaQueue")
                .visibilityTimeout(Duration.seconds(300))
                .build();

        IBucket bucket = Bucket.fromBucketName(this, "awsdoc-bucket", s3BucketName);

        Role role = Role.Builder.create(this, id+"-lambda-role")
            .assumedBy(ServicePrincipal.Builder.create("lambda.amazonaws.com").build())
            .description("Redrive lambda role")               
            .build();

        //bucket grant role access to read objects
        bucket.grantRead(role);

        //queue grant access to role to send messages
        queue.grantSendMessages(role);
        

        //add a policy to queue allowing s3 bucket access to send messages
        queue.addToResourcePolicy(PolicyStatement.Builder.create()
            .resources(Arrays.asList(queue.getQueueArn()))
            .actions(Arrays.asList("sqs:SendMessage"))
            .principals(Arrays.asList(new ServicePrincipal("s3.amazonaws.com")))
            .conditions(new HashMap<String, Object>() {{
                put("ArnEquals", new HashMap<String, String>() {{
                    put("aws:SourceArn", bucket.getBucketArn());
                }});
            }})
            .build());
        
        role.addManagedPolicy(ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"));
                
        //lambda layers
        List<ILayerVersion> layers	=	Arrays.asList(	LayerVersion.fromLayerVersionArn(this,
        "LambdaLayerRedrive", 
        "arn:aws:lambda:"+this.getRegion()+":580247275435:layer:LambdaInsightsExtension:38"));

        Function.Builder.create(this, "redrive-function")
            .runtime(Runtime.JAVA_17)
            .code(Code.fromAsset("target/redrive-lambda.jar"))
            .handler("com.example.Redrive::handleRequest")
            .memorySize(256)
            .layers(layers)
            .tracing(Tracing.ACTIVE)
            .timeout(Duration.seconds(900))
            .logRetention(RetentionDays.ONE_YEAR)
            .role(role)
            .build();                   
    } 
}
