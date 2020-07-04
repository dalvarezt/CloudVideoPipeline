package ec.ibm.cos;

import ec.ibm.eventstreams.Callback;

import java.util.Base64;
import java.util.Properties;
import java.io.ByteArrayInputStream;

import java.io.InputStream;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata;
import com.ibm.cloud.objectstorage.oauth.BasicIBMOAuthCredentials;

public class COSBridge implements Callback{
    private Properties cosConfig;
    private AmazonS3 cos;
    private String bucket;
    public COSBridge(Properties config) {
        cosConfig = config;

        AWSCredentials credentials = new BasicIBMOAuthCredentials(
            cosConfig.getProperty("apiKey"),
            cosConfig.getProperty("serviceInstanceId")
        );
        ClientConfiguration clientConfig = new ClientConfiguration()
            .withRequestTimeout(5000)
            .withTcpKeepAlive(true);

        cos = AmazonS3ClientBuilder
            .standard()
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .withEndpointConfiguration(
                new EndpointConfiguration(
                    cosConfig.getProperty("endpoint"), 
                    cosConfig.getProperty("location")
                )
            )
            .withPathStyleAccessEnabled(true)
            .withClientConfiguration(clientConfig)
            .build();
            bucket = cosConfig.getProperty("bucket");
    }
    

    public void callback(String payload) {
        try {
            JsonObject doc = JsonParser.parseString(payload).getAsJsonObject();
            String ts = doc.get("timestamp").getAsString();
            String camera = doc.get("cameraId").getAsString();
            String location = doc.get("locationName").getAsString();
            byte[] image = Base64.getDecoder().decode(
                doc.get("image").getAsString()
            );
            InputStream is = new ByteArrayInputStream(image);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("image/jpeg");
            metadata.setContentLength(image.length);
            System.out.println("Uploading image");
            cos.putObject(
                bucket,
                String.format("%s/%s/%s.jpg", new Object[]{location, camera, ts}),
                is,
                metadata
            );

        } catch (JsonSyntaxException e) {
            System.err.println("Invalid message received");
            System.err.println(payload);
            e.printStackTrace();
        }
        


    }
}
