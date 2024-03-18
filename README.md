# S3 Redrive Lambda!

It replays S3 PUT events. Scans files in your S3 bucket (based on prefix, keys or lastModifiedDate) and send S3 PUT events to a SQS queue.

This is Java 17 project that uses [Maven](https://maven.apache.org/), so you can open this project with any Maven compatible Java IDE to build and run tests.

## Architecture

<p align="center">
<img src="https://github.com/ldecaro/redrive-s3-sqs-lambda/blob/main/img/redrive-architecture.png" width=70% height=70%>
</p>

## Running

```
mvn clean package
cdk synth
cdk deploy RedriveLambda
```

## Testing

Invoke a lambda passing parameters in a payload file. Depending on the use case, there are two types of payload that can be sent to the lambda: you can choose between scanning files based on a s3 prefix (payload.json) or using keys (payload-keys.json).

1. Update the file payload.json or payload-keys.json depending on your use case. 

If using file `payload.json`:
- s3Prefix is your bucketName or bucketName/folder;
- minutes means how old you want your s3 files to be (in minutes back from now). To scan all files, choose minutes = 0; 

An example is shown below:
```
{
    "s3Prefix": "my-dead-letter-bucket/myprefix",
    "queueURL": "https://sqs.us-east-1.amazonaws.com/587929909912/RedriveLambda-RedriveLambdaQueue30239EC5-zU8oDonOIwhH",
    "minutes": 600
}
```
If using file `payload-keys.json`
- bucketName should contain only the bucket name;
- queueURL is the URL of the SQS queue you want to send PUT messages to.
- keys is an array of object keys within bucket

An example is shown below:
```
{
    "bucketName": "test-bucket-redrive",
    "queueURL": "https://sqs.us-east-1.amazonaws.com/587929909912/RedriveLambda-RedriveLambdaQueue30239EC5-zU8oDonOIwhH",
    "keys": ["test-1", "test-2"]
}
```

2. Adding 100 files to S3 bucket:
```
echo "ok ok" >> testfile
for i in {1..100}; do aws s3api put-object --bucket test-bucket-redrive --key test-$i --body testfile; done
```

3. Invoking the lambda:
```
aws lambda invoke --function-name my-lambda-function-name --payload file://payload.json output.json --cli-binary-format raw-in-base64-out
```
## Useful commands:

Reading messages from SQS queue:
```
aws sqs receive-message --queue-url $QUEUE_URL --max-number-of-messages 10 --wait-time-seconds 20 --region us-east-1
```

Clean SQS queue:
```
 aws sqs purge-queue --queue-url $QUEUE_URL
```

Enjoy!
