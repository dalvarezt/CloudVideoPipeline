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
import java.util.Date;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.Base64;
import java.util.Calendar;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;;

@WebServlet(urlPatterns="/getVideo")
public class GetVideoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    private static final String VIDEO_DIRECTORY = "tempVideoStorage";
    private static final int VIDEO_FILE_PRUNE_MINUTES=10;
    
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
		}, 2, VIDEO_FILE_PRUNE_MINUTES/2, TimeUnit.MINUTES);
		
	}
    

	/**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String location = request.getParameter("locationName");
        String camera = request.getParameter("cameraId");
        String s_startTimestamp = request.getParameter("startTimestamp");
        String s_endTimestamp = request.getParameter("endTimestamp");
        Calendar startTimestamp, endTimestamp;
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        df.setTimeZone(TimeZone.getTimeZone("CET"));
        
        try {
            startTimestamp = Calendar.getInstance();
            startTimestamp.setTime(df.parse(s_startTimestamp));
            endTimestamp = Calendar.getInstance();
            endTimestamp.setTime(df.parse(s_endTimestamp));
        } catch (ParseException pe) {
            throw new ServletException("Invalid timestamp format");
        }
        String filename = VIDEO_DIRECTORY + "/" + Base64.getEncoder().encodeToString(request.getRequestURL().toString().getBytes()) + ".mp4";
        File result = new File(filename);
        if (!result.exists() ) {
        	try {
        		producer.produceVideo(result, location, camera, startTimestamp, endTimestamp);
        	} catch(Exception e) {
        		throw new ServletException(e);
        	}
        }
        
        
    	response.setContentType("video/mp4");    	
    	FileInputStream fr = new FileInputStream(result);
    	byte[] buffer = new byte[4096];
    	int len;
    	while( (len = fr.read(buffer)) >0 ) {
    		response.getOutputStream().write(buffer, 0, len);
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
