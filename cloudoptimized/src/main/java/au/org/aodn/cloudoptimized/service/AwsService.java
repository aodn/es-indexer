package au.org.aodn.cloudoptimized.service;

import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

public class AwsService {

    @Value("${aws.region}")
    private String region;
    private static final AwsService instance = new AwsService();

    private static final S3Client s3 = S3Client.builder()
            .region(Region.of(instance.region))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .build();

    private AwsService(){}
    public static AwsService getInstance(){
        return instance;
    }
    public S3Client getS3Client(){
        return s3;
    }
}
