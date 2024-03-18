package com.example;

import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.Redrive.RedriveParams;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * @author Luiz Decaro
 */
public class Redrive implements RequestHandler<RedriveParams, String> {

    private static LambdaLogger logger = null;
    private static final SqsClient SQS_CLIENT =  SqsClient.create();
    private static final S3Client S3_CLIENT =  S3Client.create();

    public String handleRequest(Redrive.RedriveParams input, Context context) {

        Redrive.logger = context.getLogger();
        Integer messagesSent = 0;
        try{
            if( input.bucketName() != null && input.bucketName() != ""){
                messagesSent = sendMessage(input.bucketName(), input.queueURL(), input.keys());                
            }else{
                messagesSent = scanAndSend(input.s3Prefix(), input.queueURL(), input.minutes());
            }
            return "Success. Sent "+messagesSent+" messages";
        }catch(Exception e){
            logger.log("Exception: "+e.getMessage());
            e.printStackTrace();;
            return "Error. "+e.getMessage();
        }
    }

    public Integer sendMessage(String bucketName, String queueUrl, String[] keys){

        if( bucketName.indexOf("/")!=-1 && bucketName.length() > bucketName.indexOf("/")+1){
            logger.log("Bucket name is invalid. Should be only the bucket name and nothing else");
            throw new IllegalArgumentException("Bucket name is invalid. Should be only the bucket name and nothing else");
        }

        //find all keys that start with / 
        Long wrongKeys = Arrays.asList(keys).stream().filter(key-> key.startsWith("/")).collect(Collectors.counting());
        if( wrongKeys > 0){
            logger.log("keys cannot start with /. There are "+wrongKeys+" wrong keys");
            throw new IllegalArgumentException("keys cannot start with /. There are "+wrongKeys+" wrong keys");
        }

        int count = 0;
        for(String key: keys){

            ++count;
            sendMessage(bucketName, key, queueUrl);
        }
        return count;
    }

    private Integer scanAndSend(String s3Prefix, String queueUrl, Integer minutes) {

        logger.log("Sending messages to SQS");
        Integer count = 0;

        String bucketName   =   s3Prefix.indexOf("/")!=-1 ? 
            s3Prefix.substring(0, s3Prefix.indexOf('/')) 
            : s3Prefix;

        String prefix = s3Prefix.substring(s3Prefix.indexOf('/') );

        ListObjectsV2Request listReq = null;
        if(s3Prefix.indexOf("/")!= -1){
            logger.log(("Search bucket "+bucketName+" with prefix "+prefix));
            listReq =   ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix(prefix)
            .build();
        }else{
            listReq =   ListObjectsV2Request.builder()
            .bucket(bucketName)
            .build();
        }

        Instant now =   Instant.now();
        ListObjectsV2Response response  =   null;
        do {
            response = Redrive.S3_CLIENT.listObjectsV2(listReq);
            logger.log("Found "+response.keyCount()+" files");
            //list of s3 object keys separated by comma
            logger.log("Keys: "+response.contents().stream().map(S3Object::key).collect(Collectors.joining(",")));
            logger.log("Now is "+now);
            for (S3Object object : response.contents()) {

                if( minutes == 0 ){
                    ++count;
                    sendMessage(bucketName, object.key(), queueUrl);

                }else{
                    if( now.minusSeconds(minutes*60).isBefore(object.lastModified())){

                        ++count;
                        sendMessage(bucketName, object.key(), queueUrl);
                    }
                }
            }
            listReq = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .continuationToken(response.nextContinuationToken())  
                .build();

        } while (response.isTruncated());

        return count;
    }

    private void sendMessage(String bucketName, String key, String queueUrl){
        
        String messageBody = getS3PutPayload().replace("{now}", Instant.now().toString()).replace("{bucketName}", bucketName).replace("{key}", key);
        logger.log("Sending message for "+bucketName+"/"+key);
        SendMessageRequest sendReq = SendMessageRequest.builder()
            .queueUrl(queueUrl) 
            .messageBody(messageBody)
            .build();

        Redrive.SQS_CLIENT.sendMessage(sendReq);
    }

    private static String getS3PutPayload(){
        return """
            {
                "Records": [
                  {
                    "eventVersion": "2.0",
                    "eventSource": "aws:s3",
                    "awsRegion": "us-east-1",
                    "eventTime": "{now}",
                    "eventName": "ObjectCreated:Put",
                    "userIdentity": {
                      "principalId": "EXAMPLE"
                    },
                    "requestParameters": {
                      "sourceIPAddress": "127.0.0.1"
                    },
                    "responseElements": {
                      "x-amz-request-id": "EXAMPLE123456789",
                      "x-amz-id-2": "EXAMPLE123/5678abcdefghijklambdaisawesome/mnopqrstuvwxyzABCDEFGH"
                    },
                    "s3": {
                      "s3SchemaVersion": "1.0",
                      "configurationId": "testConfigRule",
                      "bucket": {
                        "name": "{bucketName}",
                        "ownerIdentity": {
                          "principalId": "EXAMPLE"
                        },
                        "arn": "arn:aws:s3:::{bucketName}"
                      },
                      "object": {
                        "key": "{key}",
                        "size": 1024,
                        "eTag": "0123456789abcdef0123456789abcdef",
                        "sequencer": "0A1B2C3D4E5F678901"
                      }
                    }
                  }
                ]
              }            
        """;
    }

    record RedriveParams(String s3Prefix, String bucketName, String queueURL, int minutes, String[] keys){}
}