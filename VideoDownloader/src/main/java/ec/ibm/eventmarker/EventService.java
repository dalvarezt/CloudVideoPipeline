package ec.ibm.eventmarker;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.ws.rs.PUT;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.ibm.cloud.objectstorage.AmazonServiceException;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata;
import com.ibm.cloud.objectstorage.services.s3.model.S3Object;
import com.ibm.cloud.objectstorage.util.StringInputStream;

@RequestScoped
@Path("event")
public class EventService {

	@Inject
	@ApplicationScoped
	private AmazonS3 cosClient;

	@Inject
	@ConfigProperty(name = "cos_bucket")
	String bucket;

	@Inject
	@ConfigProperty(name="video_max_duration_seconds", defaultValue = "180")
	private int MAX_DURATION;

	@GET
	@Path("{eventId}")
	@Produces(MediaType.APPLICATION_JSON)
	public JsonObject getEvent(@PathParam("eventId") String eventId) {

		JsonObjectBuilder builder = Json.createObjectBuilder();
		
		try {

			if (eventId==null || eventId=="" || eventId.length()>32) {
				throw new Exception("Event id can't be blank or longer than 32 characters");
			}

			String objectKey = "events/" + eventId + ".json";
	
			S3Object obj = cosClient.getObject(bucket, objectKey);
			JsonReader reader = Json.createReader(obj.getObjectContent());
			JsonStructure jsonst = reader.read();
			return jsonst.asJsonObject();
		} catch (AmazonServiceException e) {
			builder.add("status", "error");
			builder.add("message", "Event not found");
			return builder.build();
		} catch (Exception e) {
			builder.add("status", "error");
			builder.add("message", e.getMessage());
			return builder.build();
		}

	}

	@PUT
	@Path("{eventId}")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.APPLICATION_JSON)
	public JsonObject setEvent(@PathParam("eventId") String eventId, @FormParam("startTimestamp") String st,
			@FormParam("endTimestamp") String et, @FormParam("location") String location,
			@FormParam("camera") String camera) 
	{
		try {
			Instant startTimestamp, endTimestamp;

			if (eventId==null || eventId=="" || eventId.length()>32) {
				throw new Exception("Event id can't be blank or longer than 32 characters");
			}

			if(location==null || location=="" || location.length()>256) {
				throw new Exception("Invalid location. Can't be blank or longer than 256 characters.");
			}

			if (camera==null || camera=="" || camera.length()>256){
				throw new Exception("Invalid camera ID. Can't be blank or longer than 256 characters.");
			}

			if(st==null || st=="" || (startTimestamp=parseDate(st))==null){
				throw new Exception("Invalid start timestamp. Can't be blank and must follow ISO8601 format (yyyy-mm-ddThh:mm:ss.sssTZ)");
			}

			if(st==null || et=="" || (endTimestamp=parseDate(et))==null){
				throw new Exception("Invalid end timestamp. Can't be blank and must follow ISO8601 format (yyyy-mm-ddThh:mm:ss.sssTZ)");
			}

			if(startTimestamp.compareTo(endTimestamp)>=0 ) {
				throw new Exception("Start timestamp should be before end timestamp");
			}

			if(Duration.between(startTimestamp,endTimestamp).getSeconds() > MAX_DURATION) {
				throw new Exception("Time span between start and end time stamps shouldn't be longer than " + MAX_DURATION + " seconds");
			}

			String[] loc = location.split(",");
			String[] cam = camera.split(",");

			if(loc.length != cam.length) {
				throw new Exception("The number of locations and cameras should be the same");
			}

			JsonObjectBuilder builder = Json.createObjectBuilder();
			builder.add("eventId", eventId)
			.add("startTimeStamp", DateTimeFormatter.ISO_INSTANT.format(startTimestamp))
			.add("endTimestamp", DateTimeFormatter.ISO_INSTANT.format(endTimestamp));
			JsonArrayBuilder sources = Json.createArrayBuilder();

			for (int i = 0; i < loc.length; i++) {
				JsonObjectBuilder source = Json.createObjectBuilder();
				source.add("location", loc[i]).add("camera", cam[i]);
				sources.add(source);
			}
			builder.add("videoSources", sources);
			
			String doc = builder.build().toString();
			StringInputStream stream = new StringInputStream(doc);
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentType("application/json");
			metadata.setContentLength(doc.getBytes().length);
			cosClient.putObject(bucket, "events/" + eventId + ".json", stream, metadata);
			JsonObjectBuilder res = Json.createObjectBuilder();
			res.add("status", "success");
			return res.build();
		} catch (Exception e) {
			JsonObjectBuilder er = Json.createObjectBuilder();
				er.add("status", "error").add("message", e.getMessage());
				return er.build();
		}
	}

	private Instant parseDate(String ds) {
		try {
			TemporalAccessor ta = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(ds);
			return Instant.from(ta);
		} catch(DateTimeParseException e) {
			return null;
		}
	}

}
