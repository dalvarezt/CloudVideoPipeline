/*******************************************************************************
 * (c) Copyright IBM Corporation 2017.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package ec.ibm.video;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.microprofile.config.inject.ConfigProperty;



@WebServlet(urlPatterns="/getVideo")
public class GetVideoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    private static final String VIDEO_DIRECTORY = "tempVideoStorage";
    private static final int VIDEO_FILE_PRUNE_MINUTES=10;
    
    @Inject
    @ConfigProperty(name="video_max_duration_seconds", defaultValue = "180")
    private long MAX_VIDEO_DURATION;

    @Inject @ApplicationScoped
    private VideoProducer producer;
    
    @Resource
    ManagedScheduledExecutorService scheduledExecutor;

        
    @Override
	public void init() throws ServletException {
		super.init();
		System.out.println("Initializing GetVideoServlet");
		//Create video store directory
		File dir = new File(VIDEO_DIRECTORY);
		if(!dir.exists()) {
			dir.mkdir();
		}
		
		scheduledExecutor.scheduleAtFixedRate(()->{
			System.out.println("Prunning video directory");
	    	File directory = new File(VIDEO_DIRECTORY);
	    	File[] files = directory.listFiles();
	    	for (File f : files) {
	    		Calendar now = Calendar.getInstance();
	    		now.setTime(new Date());
	    		if (now.getTimeInMillis() - f.lastModified() > VIDEO_FILE_PRUNE_MINUTES*60*1000) {
	    			f.delete();
	    		}
	    	}
		}, VIDEO_FILE_PRUNE_MINUTES, VIDEO_FILE_PRUNE_MINUTES, TimeUnit.MINUTES);
		
    }
    
    

	/**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String location = request.getParameter("locationName");
        String camera = request.getParameter("cameraId");
        String s_startTimestamp = request.getParameter("startTimestamp");
        String s_endTimestamp = request.getParameter("endTimestamp");
        Instant startTimestamp, endTimestamp;
        
        try {
            TemporalAccessor ta;
            ta = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s_startTimestamp);
            startTimestamp = Instant.from(ta);
        } catch (DateTimeParseException pe) {
            throw new ServletException("Invalid timestamp format for start timestamp");
        }

        try {
            TemporalAccessor ta;
            ta = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s_endTimestamp);
            endTimestamp = Instant.from(ta);
        } catch (DateTimeParseException pe) {
            throw new ServletException("Invalid timestamp format for end timestamp");
        }

        if(startTimestamp.compareTo(endTimestamp)>=0){
            throw new ServletException("Start timestamp should be before end timestamp");
        }

        if (Duration.between(startTimestamp, endTimestamp).getSeconds() > MAX_VIDEO_DURATION) {
            throw new ServletException("Video duration exeeds limits");
        }

        
        // This allows to chache recently produced videos on the file system
        String filename = VIDEO_DIRECTORY + "/" + Base64.getEncoder().encodeToString(request.getQueryString().getBytes()) + ".mp4";
        File videoFile = new File(filename);
        if (!videoFile.exists() ) {
        	try {
                producer.produceVideo(videoFile, location, camera, startTimestamp, endTimestamp);
        	} catch(Exception e) {
        		throw new ServletException(e);
        	}
        }
        
        
        response.setContentType("video/mp4");
        //response.setContentLength((int) videoFile.getTotalSpace());
        FileInputStream fr = new FileInputStream(videoFile);
    	byte[] buffer = new byte[4096];
    	int len;
    	while( (len = fr.read(buffer)) > 0 ) {
            try {
                response.getOutputStream().write(buffer, 0, len);
            } catch (IOException e) {
                System.out.println("Error writing buffer of length " +  len);
                e.printStackTrace();
                break;
            }
    	}
    	fr.close();
    	response.getOutputStream().flush();
    }

    

    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }
}
