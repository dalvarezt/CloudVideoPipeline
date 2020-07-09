package ec.ibm.video;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.context.ApplicationScoped;
import javax.imageio.ImageIO;
import javax.inject.Inject;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectListing;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Rational;

import ec.ibm.video.context.AppContext;


@ApplicationScoped
public class VideoProducer {
	@Inject
    @ConfigProperty(name="cos_bucket")
	private String bucket;
	
	@Inject
	@ConfigProperty(name="cos_concurrent_threads", defaultValue="6")
	private int COS_CONCURRENT_THREADS;
	
	@Inject @AppContext
    ManagedExecutor executor;
	
	@Inject @ApplicationScoped
	private AmazonS3 cosClient;
	
	public static final String DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";
	
	/**
	 * Locates and downloads frames from COS and generates a video file with them
	 * @param file The file on which the video is stored
	 * @param location
	 * @param camera
	 * @param startTimestamp
	 * @param endTimestamp
	 * @throws Exception
	 */
	public void produceVideo(
			File file, 
			String location, 
			String camera, 
			Instant startTimestamp, 
			Instant endTimestamp 
	) throws Exception {
	
		SeekableByteChannel out;
		AWTSequenceEncoder encoder;
		PriorityQueue<String> objectPaths = this.getFilePaths(location, camera, startTimestamp, endTimestamp);
		Rational fps = getFPS(objectPaths.toArray());
		try {
			out = NIOUtils.writableChannel(file);
			encoder = new AWTSequenceEncoder(out, fps);
		} catch (FileNotFoundException e) {
			throw new Exception(e);
		} catch(IOException e) {
			throw new Exception(e);
		}
		
		
		Function<InputStream,BufferedImage> readImage = (is -> {
			try {
				BufferedImage img = ImageIO.read(is);
				return img;
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		});
		
		Consumer<BufferedImage> encodeImage = (image->{
			try {
				encoder.encodeImage(image);
			} catch (IOException e) {
				e.printStackTrace();
			}		
		});
		
		BiFunction<InputStream,Throwable,Void> exHandler = (t,e) -> {
			System.out.println("Object fetch failed");
			e.printStackTrace();
			return null;
		};
		
		int batchSize = COS_CONCURRENT_THREADS;
		BlockingQueue<CompletableFuture<InputStream>> storage = new ArrayBlockingQueue<>(batchSize);
		
		while( !objectPaths.isEmpty()) {
			for (int i=0; i<batchSize; i++) {
				String objKey = objectPaths.poll();
				if(objKey==null) {
					break;
				} else {
					storage.offer(executor.supplyAsync(()->{
						System.out.println("Fetching: "+ objKey);
						return cosClient.getObject(bucket, objKey).getObjectContent();
						
					}));
				}
			}
			
			while(!storage.isEmpty()) {
				if (storage.peek().isCompletedExceptionally()) { 
					storage.poll().handle(exHandler);
				} else if(storage.peek().isDone() ) {
					storage.poll().thenApply(readImage).thenAccept(encodeImage);
				} else {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						System.out.println("Who interrupted my sleep?");
						e.printStackTrace();
					}
				}
			}
		}
		
		encoder.finish();
		
		
	}
	/**
	 * Calculates frames per second from an ordered array of Object keys
	 * @param paths
	 * @return
	 */
	private Rational getFPS(Object[] paths) {
		
		try {
			Instant first = this.getObjectTimestamp((String)paths[0]);
			Instant last = this.getObjectTimestamp((String)paths[paths.length-1]);
			int duration = (int)Duration.between(first, last).getSeconds();
			return Rational.R(paths.length, duration);
		} catch (Exception e) {
			e.printStackTrace();
			return Rational.R(16, 1);
		}
	}
	/**
	 * Extracts the timestamp of an object key as a Calendar
	 * @param objectKey The object key as stored in COS
	 * @return
	 * @throws Exception
	 */
	private Instant getObjectTimestamp(String objectKey) throws Exception {
        Pattern timePattern = Pattern.compile("\\/(\\d{4}-\\d{2}-\\d{2})\\/(\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z{0,1})\\.jpg");
        Matcher m = timePattern.matcher(objectKey);
        Instant result;
        if (m.find()) {
			TemporalAccessor ta = DateTimeFormatter.ISO_INSTANT.parse(m.group(1) + "T" + m.group(2));
        	result = Instant.from(ta);
        } else {
			System.out.println("Pattern matching failed");
        	throw new Exception("Invalid date on object path");
        	//return null;
        }
        return result;
	}
	
	/**
	 * Generates an ordered collection of the COS file paths that match 
	 * the given parameters
	 * @param location
	 * @param camera
	 * @param startTimestamp
	 * @param endTimestamp
	 * @return
	 */
	private PriorityQueue<String> getFilePaths(String location, String camera, Instant startTimestamp, Instant endTimestamp){
		PriorityQueue<String> result = new PriorityQueue<String>();

		List<String> searchKeys = getSearchKeys(location, camera, startTimestamp, endTimestamp);
		List<S3ObjectSummary> summaries=null;
		for(String searchKey : searchKeys) {
			System.out.println("Search key: " + bucket + ":" +searchKey);
			ObjectListing searchResults = this.cosClient.listObjects(this.bucket, searchKey);
			if (summaries == null) {
				summaries = searchResults.getObjectSummaries();
			} else {
				summaries.addAll(searchResults.getObjectSummaries());
			}
	
		}
        
        System.out.println("Objects found: " + summaries.size());
        for (S3ObjectSummary os : summaries) {
            try {
                String objectKey = os.getKey();
				Instant objDate = getObjectTimestamp(objectKey);
                if( 
					(startTimestamp.equals(objDate) || startTimestamp.isBefore(objDate)) && 
					(endTimestamp.equals(objDate) || endTimestamp.isAfter(objDate)) 
				){
					result.offer(objectKey);
				}
            } catch(Exception e){
            	System.out.println("Invalid date found on object key:" + os.getKey());
            }
            
        }
		
		return result;
	}
	
	/**
	 * Generates a set of keys separated by 1 minute that can be used to fetch all objects
	 * that correspond to the given parameters.
	 * @param location
	 * @param camera
	 * @param startTimestamp
	 * @param endTimestamp
	 * @return
	 */
	private List<String> getSearchKeys(String location, String camera, Instant startTimestamp, Instant endTimestamp) {
		ArrayList<String> result = new ArrayList<>();
		
		Instant i = startTimestamp.truncatedTo(ChronoUnit.MINUTES);
		do {
			LocalDateTime ldt = i.atZone(ZoneOffset.UTC).toLocalDateTime();
			StringBuilder sb = new StringBuilder()
				.append(location).append("/").append(camera).append("/")
				.append(ldt.getYear()).append("-");
				if (ldt.getMonthValue()<10) {
					sb.append("0");
				}
				sb.append(ldt.getMonthValue()).append("-");
				if(ldt.getDayOfMonth()<10) {
					sb.append("0");
				}
				sb.append(ldt.getDayOfMonth())
				.append("/");
				if (ldt.getHour() < 10) {
					sb.append("0");
				}
				sb.append(ldt.getHour()).append(":");
				if(ldt.getMinute()<10) {
					sb.append("0");
				}
				sb.append(ldt.getMinute()).append(":");
				result.add(sb.toString());
				i = i.plus(1, ChronoUnit.MINUTES);
		} while(i.compareTo(endTimestamp.truncatedTo(ChronoUnit.MINUTES))<=0);

		return result;
	}
    
}
