package ec.ibm.video;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TimeZone;
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

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jcodec.api.awt.AWTSequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Rational;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectListing;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;

import ec.ibm.video.context.AppContext;


@ApplicationScoped
public class VideoProducer {
	@Inject
    @ConfigProperty(name="cos_bucket")
    private String bucket;
	
	@Inject @AppContext
    ManagedExecutor executor;
	
	@Inject @ApplicationScoped
    private AmazonS3 cosClient;
	
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
			Calendar startTimestamp, 
			Calendar endTimestamp 
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
		
		int batchSize = 4;
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
			Calendar first = this.getObjectTimestamp((String)paths[0]);
			Calendar last = this.getObjectTimestamp((String)paths[paths.length-1]);
			int duration = (int)( (last.getTimeInMillis()-first.getTimeInMillis())/1000 );
			return Rational.R(paths.length, duration);
		} catch (ParseException e) {
			e.printStackTrace();
			return Rational.R(16, 1);
		}
	}
	/**
	 * Extracts the timestamp of an object key as a Calendar
	 * @param objectKey The object key as stored in COS
	 * @return
	 * @throws ParseException
	 */
	private Calendar getObjectTimestamp(String objectKey) throws ParseException {
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        df.setTimeZone(TimeZone.getTimeZone("CET"));
        Pattern timePattern = Pattern.compile("\\/(\\d{4}-\\d{2}-\\d{2})\\/(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\d{0,3}\\.jpg");
        Matcher m = timePattern.matcher(objectKey);
        Calendar result = Calendar.getInstance();
        if (m.find()) {
        	result.setTime(df.parse(m.group(1) + "T" + m.group(2)));
        } else {
        	throw new ParseException("Invalid date on object path", 0);
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
	private PriorityQueue<String> getFilePaths(String location, String camera, Calendar startTimestamp, Calendar endTimestamp){
		PriorityQueue<String> result = new PriorityQueue<String>();
		if (startTimestamp.get(Calendar.DAY_OF_YEAR) < endTimestamp.get(Calendar.DAY_OF_YEAR)) {
            Calendar pivotTimestamp = Calendar.getInstance();
            pivotTimestamp.set(Calendar.DAY_OF_YEAR, startTimestamp.get(Calendar.DAY_OF_YEAR));
            pivotTimestamp.set(Calendar.HOUR_OF_DAY, 23);
            pivotTimestamp.set(Calendar.MINUTE, 59);
            pivotTimestamp.set(Calendar.SECOND, 59);
            pivotTimestamp.set(Calendar.MILLISECOND,999);
            result.addAll( this.getFilePaths(location, camera, startTimestamp, pivotTimestamp) );
            pivotTimestamp.add(Calendar.MILLISECOND, 1);
            result.addAll( this.getFilePaths(location, camera, pivotTimestamp, endTimestamp) );
            
            return result;
        }
		
        String searchKey = getOptimalKey(location, camera, startTimestamp, endTimestamp);
        System.out.println("Search key: " + bucket + ":" +searchKey);
        ObjectListing searchResults = this.cosClient.listObjects(this.bucket, searchKey);
        List<S3ObjectSummary> summaries = searchResults.getObjectSummaries();
        if (summaries.isEmpty()) {
        	System.err.println("No files found with given parameters");
            return null;
        }
        while (searchResults.isTruncated()) {
            searchResults = this.cosClient.listObjects(this.bucket, searchResults.getNextMarker());
            summaries.addAll(searchResults.getObjectSummaries());
        }
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        df.setTimeZone(TimeZone.getTimeZone("CET"));
        
        System.out.println("Objects found: " + summaries.size());
        for (S3ObjectSummary os : summaries) {
            try {
                String objectKey = os.getKey();
                Calendar objDate = getObjectTimestamp(objectKey);
                if( startTimestamp.compareTo(objDate)<=0 && endTimestamp.compareTo(objDate) >=0 ){
                   result.add(objectKey);
                }
            } catch(ParseException e){
            	System.out.println("Invalid date found on object key:" + os.getKey());
            }
            
        }
		
		return result;
	}
	
	/**
	 * Calculates the key of minimum length that matches the given 
	 * timestamps.
	 * @param location
	 * @param camera
	 * @param startTimestamp
	 * @param endTimestamp
	 * @return
	 */
    private String getOptimalKey(String location, String camera, Calendar startTimestamp, Calendar endTimestamp){
        String startKey = getFullKey(location, camera, startTimestamp);
        String endKey = getFullKey(location, camera, endTimestamp);
        System.out.println(startKey);
        String result="";
        for (int i=0; i<Math.max(startKey.length(), endKey.length()); i++){
            if (startKey.charAt(i) == endKey.charAt(i)){
                result += startKey.charAt(i);
            } else {
                break;
            }
        }
        return result;
    }
    
    /**
     * Generates the search key for an object
     * @param location
     * @param camera
     * @param ts
     * @return
     */
    private String getFullKey(String location, String camera, Calendar ts){
    	SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'/'HH:mm:ss.SSS");
    	
    	df.setTimeZone(TimeZone.getTimeZone("CET"));
        return String.format("%s/%s/%s", new Object[]{
            location, 
            camera, 
            df.format(ts.getTime())
        });
    }
	
}
