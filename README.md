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

## Architecture Overview

![Architecture Overview Diagram - IT System View](/doc/img/aod.png?raw=true)

### Description

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

### Additional Notes

As noted, this is an MVP and there is clearly some work to be done to have it
production ready. The following functionalities/characteristics are candidates
to be included in further releases:

- The serverless functions and object detection models have not yet been built
- Authentication and access control for services and accessing videos
- High availability mechanism for the video stream grabber
- Reading the timestamps from the RTSP metadata (this is currently not supported
by the OpenCV Python SDK but there are some implementations on the internet that
explain how to accomplish this)

## Getting Started

### IBM Cloud Prerequisites

As depicted in the architecture overview, in order to deploy the components,
some IBM Cloud services are needed:

- **Event Streams:**
An Event Streams instance is required with a least one Topic. On the initial
tests performed, at least one partition is needed per each video feed of 4
frames per second. If the FPS is incresed to 16, 2 partitions per stream are
needed.

- **Cloud Object Storage:** 
One instance of COS is required and also a bucket to store both video frames 
and event data. Also, the components require an API key with read/write access
to the bucket. 

- **Kubernetes Cluster:**
All components have been tested as containeraized workloads. Each has a 
`Dockerfile` descriptor and a `deployment.yml` example file. Sizing of
the cluster will vary with the number of video streams to process and 
the number of users requesting videos.

### Component Deployment

Each component has one or a couple configuration files that neet to be created
in order prior to deployment. An sample has been included in the repository as
a guide.

#### Video Grabber
Built on Python with [OpenCV](https://pypi.org/project/opencv-python/) wrapped
inside a convenient library called [rtsp](https://pypi.org/project/rtsp/). This
module connects to the video streams and divides it into frames, posting them
along with its metadata to a topic in IBM Event Streams, as JSON objects in 
which the image goes base64 encoded. 

This module has two configuration files that must be located in the `conf`
directory:

1. `kafka-settings.yml` (Event Streams configuration)

```yaml
bootstrap.servers: <comma-separated-list-of-brokers>
sasl.username: token
sasl.password: <password>
sasl.mechanism: PLAIN
security.protocol: SASL_SSL
api.version.request: True
broker.version.fallback: 0.10.2.1
log.connection.close: False
client.id: image-grabber
default.topic.config:
  request.required.acks: all
#debug: broker,topic,metadata
```


The values for `bootstrap-servers` and `sasl.password` come out of the Event 
Streams credentials.

2. `stream_settings.yml` (Video streams configuration)

```yaml
stream_sources:
 - source: 
    streamURI: rtsp://170.93.143.139/rtplive/470011e600ef003a004ee33696235daa
    cameraId: westbound-lane
    locationName: highway
    FPS: 4
 - source:
    streamURI: rtsp://user:password@freja.hiof.no:1935/rtplive/definst/hessdalen03.stream
    cameraId: woods
    locationName: hessdalen03
    FPS: 4
```

As seen, this file allows the configuration of one or more video sources. 
The initial tests shown that a single instance of this module works better 
with 2 simultaneous sources. 

#### COS Bridge

This component uses the Java SDK for Kafka to read all incoming message from a 
topic; then it decodes the frame and stores it in Cloud Object Storage as a 
JPEG file. The name of the file is generated using the frame metadata following
this pattern:

```
[location]/[cameraID]/[UTC-date]/[UTC-time].jpg
```

There are two configuration files that must be stored inside the `resources`
directory:

1. `cos.properties` (Cloud Object Storage configuration)

```
apiKey=<api-key>
serviceInstanceId=<instance-id>
endpoint=s3.us-south.cloud-object-storage.appdomain.cloud
location=us
bucket=cos-video-feed
```

The variables `apiKey` and `serviceInstanceId` come from the bucket credentials

2. `kafka.properties` (Event streams configuration)

```
bootstrap.servers=<comma-separated-list-of-brokers>
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="token" password="<password>";
sasl.mechanism=PLAIN
security.protocol=SASL_SSL
ssl.protocol=TLSv1.2
```

#### VideoDownloader

There are two components included here: a servlet and class model that performs
the assembly and delivery of videos, upon request; and the event-marker API.
The later could later be implemented using serverless functions but, given its
simplicity stage, it was found to be quicker to mingle both together.
These modules use open-liberty and a set of dependencies documented at the 
`pom.xml` file.

Module configurations are placed on the file 
`src/main/resources/META-INF/microprofile-config.properties`:

```
cos_apiKey=<api-key>
cos_serviceInstanceId=<instance-id>
cos_endpoint=s3.us-south.cloud-object-storage.appdomain.cloud
cos_location=us
cos_bucket=cos-video-feed
cos_concurrent_threads=10
video_max_duration_seconds=180
```
