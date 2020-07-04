package ec.ibm.eventstreams;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.time.Duration;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.errors.WakeupException;

/**
 * Kafka consumer thread
 */
public class Consumer implements Runnable {
	
	private final KafkaConsumer<String, String> kafkaConsumer;
    private volatile boolean closing = false;
    private Callback callback;
	
	/**
	 * 
	 * @param consumerProperties - Configuration properties
	 * @param topic - Name of the topic to listen
	 * @param cb - Callback implementation to which read messages are passed
	 */
    public Consumer(Properties consumerProperties, String topic, Callback cb) {
    	this.callback=cb;
    	kafkaConsumer = new KafkaConsumer<String, String>(consumerProperties);
    	List<PartitionInfo> partitions = kafkaConsumer.partitionsFor(topic);
    	if (partitions == null || partitions.isEmpty()) {
    		System.err.println("Topic '" + topic + "' does not exist");
    		kafkaConsumer.close();
    	} else {
    		kafkaConsumer.subscribe(Arrays.asList(topic));
    	}
    	
    }
	
	/**
	 * Starts polling the topic periodically and calls the Callback.callback method
	 * when a message is received. Only the "value" is passed to the callback
	 */
	public void run() {
		try {
			while (!closing) {
				try {
					ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(3000L));
					if (!records.isEmpty()) {
						for (ConsumerRecord<String, String> record : records) {
							
							
							this.callback.callback(record.value());
						}

					}
				} catch (final WakeupException we) {
					System.out.println("Got Wakeup Exception");
					we.printStackTrace();
				} catch (final KafkaException ke) {
					System.out.println("Kafka exception:");
					ke.printStackTrace();
					try {
						Thread.sleep(5000);
					} catch (InterruptedException ie) {
						ie.printStackTrace();
					}
				}
			} 
		} finally {
			System.out.println("Consumer closing");
			kafkaConsumer.close();
		}

	}
	/**
	 * Signals the closure of the thread
	 */
	public void shutdown() {
		closing = true;
		kafkaConsumer.wakeup();
		
	}

}
