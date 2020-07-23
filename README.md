# Cloud Video Pipeline
This project contains a set of MVP assets that create a pipeline for AI 
analysis of video sources.

This project begun with the request of a company that uses IBM Food Trust to 
manage the traceability of one of its product lines and has developed a web app
that allows consumers to query the traceability data by scanning a QR label.
The customer wanted to be able to include videos taken from the production plant
while each product batch was going through each production stage. Also, they 
wanted to use object detection to show that security measures are being always 
followed by their personel, like the use of face masks, bio-security suits and 
gloves. 

There are many video sources and, potentially, many object detection models to 
be applied to each.

# Architecture Overview

![Architecture Overview Diagram - IT System View](/doc/img/aod.png?raw=true)

## Description

1. **Frame Capture**

	The video grabber component connects through RTSP to one or many video 
	sources, that have previously been configured. This component breaks 
	the video on individual frames. Each frame is resized based on a preset 
	and sent to an Event Streams topic along with image metadata like, 
	location, camera id and the capture timestamp. It is possible to 
	configure each video source to handle a maximum number of frames 
	per second. 

2. **Object Detection Gateway**

	A serverless function is triggered with each incoming frame on the Event
	Streams topic, grabs it and calls an Object Detection/AI Visual Processing
	model. The result of this processing is posted back on another Event 
	Streams topic. This allows chaining the call of multiple models per frame 
	for different purposes.
	
3. **Event Detection**

	If the executed model detects an event worth reporting, the function can 
	invoke the event recording API.

4. **Frame Storage**

	This component monitors an Event Streams topic and grabs each incoming 
	message, storing the image binary on Cloud Object storage, using its 
	metadata to generate the object name. 

5. **Event Recording**

	A convenience API is provided to generate "events", which have the 
	attributes of the video source(s) to which the event is related and the 
	start and end timestamps. Thus, it could be possible extract the video 
	clip(s) related to the event.
	Events are stored as JSON objects at Object Storage, at the moment.
	
6. **Video Assembly**

	The video producer component takes the video metadata and start/end 
	timestamps to obtain the frames out of COS to generate and send an mp4 
	video that is delivered through HTTP.
	








