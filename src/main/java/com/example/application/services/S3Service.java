package com.example.application.services;

import com.example.application.FileSystemItem;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@Service
public class S3Service {

    @Value("${aws.s3.access-key-id}")
    private String accessKeyId;

    @Value("${aws.s3.secret-access-key}")
    private String secretAccessKey;

    @Value("${aws.s3.endpoint}")
    private String endpoint;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;
    public void createFolder(String folderName) {
        if (!folderName.endsWith("/")) {
            folderName += "/";
        }
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(folderName)
                .build();
        s3Client.putObject(putObjectRequest, software.amazon.awssdk.core.sync.RequestBody.fromBytes(new byte[0]));
    }
    private S3Client s3Client;
    public void uploadFile(String key, InputStream inputStream) throws IOException {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.putObject(putObjectRequest, software.amazon.awssdk.core.sync.RequestBody.fromInputStream(inputStream, inputStream.available()));
    }
    @PostConstruct
    public void init() throws URISyntaxException {
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        this.s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(new URI(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();
    }

    public List<FileSystemItem> listS3Objects(String prefix) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .delimiter("/")
                .build();

        ListObjectsV2Response result = s3Client.listObjectsV2(request);

        List<FileSystemItem> items = new ArrayList<>();
        for (var commonPrefix : result.commonPrefixes()) {
            items.add(new FileSystemItem(commonPrefix.prefix(), true));
        }
        for (S3Object s3Object : result.contents()) {
            if (!s3Object.key().equals(prefix)) {
                items.add(new FileSystemItem(s3Object.key(), false));
            }
        }

        return items;
    }
    public List<FileSystemItem> searchFiles(String query) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(query)
                .build();

        ListObjectsV2Response result = s3Client.listObjectsV2(request);

        List<FileSystemItem> items = new ArrayList<>();
        for (S3Object s3Object : result.contents()) {
            items.add(new FileSystemItem(s3Object.key(), false));
        }

        return items;
    }
    public Object getEndpoint() {
        return endpoint;
    }

    public InputStream getFileStream(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        return s3Client.getObject(getObjectRequest);
    }
}
