package ec.ibm.video.cos;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import com.ibm.cloud.objectstorage.ClientConfiguration;
import com.ibm.cloud.objectstorage.auth.AWSCredentials;
import com.ibm.cloud.objectstorage.auth.AWSStaticCredentialsProvider;
import com.ibm.cloud.objectstorage.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.ibm.cloud.objectstorage.oauth.BasicIBMOAuthCredentials;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3ClientBuilder;

/**
 * 
 * @author GuillermoDanielAlvar
 *
 */
@ApplicationScoped
public class COSClient {
	
	Config config = ConfigProvider.getConfig();
	
	private String apiKey = config.getValue("cos_apiKey", String.class);
	private String serviceInstanceId = config.getValue("cos_serviceInstanceId", String.class);
	private String endpoint = config.getValue("cos_endpoint", String.class);
	private String location = config.getValue("cos_location", String.class);
	private String bucket = config.getValue("cos_bucket", String.class);
	
    @Produces @ApplicationScoped 
    AmazonS3 cosClient = getClient();

    public AmazonS3 getClient() {
    	ClientConfiguration clientConfig = new ClientConfiguration()
    		    .withRequestTimeout(15000)
    		    .withTcpKeepAlive(true);
    	AWSCredentials credentials = new BasicIBMOAuthCredentials(apiKey, serviceInstanceId);
    	return AmazonS3ClientBuilder
         .standard()
         .withCredentials(new AWSStaticCredentialsProvider(credentials))
         .withEndpointConfiguration(new EndpointConfiguration(endpoint, location))
         .withPathStyleAccessEnabled(true)
         .withClientConfiguration(clientConfig)
         .build();
    }
    
    public String getBucket() {
    	return bucket;
    }
    
}