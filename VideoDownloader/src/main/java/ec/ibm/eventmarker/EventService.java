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

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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
	
	@Inject @ApplicationScoped
    private AmazonS3 cosClient;
	
	@Inject @ConfigProperty(name="cos_bucket")
	String bucket;
	
	@GET
	@Path("{eventId}")
	@Produces(MediaType.APPLICATION_JSON)
	public JsonObject getEvent(@PathParam("eventId") String eventId) {
		JsonObjectBuilder builder = Json.createObjectBuilder();
		String objectKey = "events/" + eventId + ".json";
		try {
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
	public JsonObject setEvent( 
			@PathParam("eventId") String eventId,
			@FormParam("startTimestamp") String st,
			@FormParam("endTimestamp") String et,
			@FormParam("location") String location,
			@FormParam("camera") String camera
			) 
	{
		
		if (!isDateValid(st) || !isDateValid(et)) {
			JsonObjectBuilder er = Json.createObjectBuilder();
			er.add("status", "error").add("message", "Invalid date format");
			return er.build();
		}

		String[] loc = location.split(",");
		String[] cam = camera.split(",");

		JsonObjectBuilder builder = Json.createObjectBuilder();
		builder
			.add("eventId", eventId)
			.add("startTimeStamp", st)
			.add("endTimestamp", et);
		JsonArrayBuilder sources = Json.createArrayBuilder();
		
		for(int i=0; i< loc.length; i++) {
			JsonObjectBuilder source = Json.createObjectBuilder();
			source.add("location", loc[i])
				.add("camera", cam[i]);
			sources.add(source);
		}
		builder.add("videoSources", sources);
		try {
			String doc = builder.build().toString();
			StringInputStream stream = new StringInputStream(doc);
			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentType("application/json");
			metadata.setContentLength(doc.getBytes().length);
			cosClient.putObject(bucket, "events/"+eventId+".json", stream, metadata);
			JsonObjectBuilder res = Json.createObjectBuilder();
			res.add("status", "success");
			return res.build();
			
		} catch (Exception e) {
			JsonObjectBuilder er = Json.createObjectBuilder();
			er.add("status", "error").add("message", e.getMessage());
			return er.build();
		}
	}

	private boolean isDateValid(String ds) {
		DateTimeFormatter f = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
		try {
			f.parse(ds);
			return true;
		} catch(DateTimeParseException e) {
			return false;
		}
	}
}
