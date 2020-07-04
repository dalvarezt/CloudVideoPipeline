import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.config.SslConfigs;
import ec.ibm.eventstreams.Consumer;
import ec.ibm.cos.COSBridge;

public class App {
    private Properties kafkaProperties;
    private Properties cosProperties;
	private static Thread consumerThread = null;
	private static Consumer consumer;
    private static final String CLIENT_ID =  "image-consumer";
    private static final String GROUP_ID = "image-consumer-group";
    private static final String TOPIC = "images";
	static {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("Shutdown received.");
                shutdown();
			}
		});

	}

	public static void main(String[] args){
        new App();
    }

    private App() {
        try {
            InputStream is = this.getClass().getResourceAsStream("kafka.properties");
            kafkaProperties = new Properties();
            kafkaProperties.load(is);
			kafkaProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
			kafkaProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
			kafkaProperties.put(ConsumerConfig.CLIENT_ID_CONFIG, CLIENT_ID);
			kafkaProperties.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
			kafkaProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
			kafkaProperties.put(ConsumerConfig.CLIENT_DNS_LOOKUP_CONFIG,"use_all_dns_ips");
			kafkaProperties.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "HTTPS");
        } catch(IOException e) {
            System.err.println("Error reading kafka configuration file");
            e.printStackTrace();
        }

        try {
            InputStream is = this.getClass().getResourceAsStream("cos.properties");
            cosProperties = new Properties();
            cosProperties.load(is);
            COSBridge bridge = new COSBridge(cosProperties);
            App.consumer = new Consumer(kafkaProperties, TOPIC, bridge);
            App.consumerThread = new Thread(consumer, "Consumer Thread");
            consumerThread.start();
        } catch( Exception e){
            e.printStackTrace();
        }
    }

    private static void shutdown() {
        if (App.consumerThread!=null) {
            App.consumer.shutdown();
        }
    }
}