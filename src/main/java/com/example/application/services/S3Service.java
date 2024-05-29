package com.example.application.services;

import com.example.application.FileSystemItem;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
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
    private S3Client s3Client;

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
            items.add(new FileSystemItem(commonPrefix.prefix(), 0, true));
        }
        for (S3Object s3Object : result.contents()) {
            if (!s3Object.key().equals(prefix)) {
                items.add(new FileSystemItem(s3Object.key(), s3Object.size(), false));
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
            items.add(new FileSystemItem(s3Object.key(), s3Object.size(), false));
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

    public void deleteFile(String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        var response = s3Client.deleteObject(deleteObjectRequest);


    }

    public void deleteFile(String key, boolean deleteAllVersions) {
        if (deleteAllVersions) {
            // List all versions of the object and delete each one
            ListObjectVersionsRequest listObjectVersionsRequest = ListObjectVersionsRequest.builder()
                    .bucket(bucketName)
                    .prefix(key)
                    .build();

            ListObjectVersionsResponse listObjectVersionsResponse = s3Client.listObjectVersions(listObjectVersionsRequest);

            List<ObjectIdentifier> objectIdentifiers = new ArrayList<>();

            for (var version : listObjectVersionsResponse.versions()) {
                if (version.key().equals(key)) {
                    objectIdentifiers.add(ObjectIdentifier.builder()
                            .key(version.key())
                            .versionId(version.versionId())
                            .build());
                }
            }

            for (DeleteMarkerEntry deleteMarker : listObjectVersionsResponse.deleteMarkers()) {
                if (deleteMarker.key().equals(key)) {
                    objectIdentifiers.add(ObjectIdentifier.builder()
                            .key(deleteMarker.key())
                            .versionId(deleteMarker.versionId())
                            .build());
                }
            }

            if (!objectIdentifiers.isEmpty()) {
                DeleteObjectsRequest deleteObjectsRequest = DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(Delete.builder().objects(objectIdentifiers).build())
                        .build();

                s3Client.deleteObjects(deleteObjectsRequest);
            }
        } else {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
        }
    }

    public void deleteFolder(String folderKey) {
        // Ensure folder key ends with '/'
        if (!folderKey.endsWith("/")) {
            folderKey += "/";
        }

        // List all objects in the folder
        List<FileSystemItem> items = listS3Objects(folderKey);

        // Delete all objects in the folder
        for (FileSystemItem item : items) {
            if (item.isFolder()) {
                deleteFolder(item.getName());
            } else {
                deleteFile(item.getName(), true);
            }
        }

        deleteFile(folderKey, true);

        // Verify that the folder is deleted
        if (isFolderDeleted(folderKey)) {
            System.out.println("Folder " + folderKey + " has been successfully deleted.");
        } else {
            System.err.println("Failed to delete folder " + folderKey + ".");
        }
    }

    private boolean isFolderDeleted(String folderKey) {
        List<FileSystemItem> items = listS3Objects(folderKey);
        return items.isEmpty();
    }

    public void renameFile(String oldKey, String newKey) {
        // Copy the object to the new key
        CopyObjectRequest copyObjectRequest = CopyObjectRequest.builder()
                .sourceBucket(bucketName)
                .sourceKey(oldKey)
                .destinationBucket(bucketName)
                .destinationKey(newKey)
                .build();

        s3Client.copyObject(copyObjectRequest);

        // Delete the old object
        deleteFile(oldKey, true); // Pass true to delete all versions
    }

}
