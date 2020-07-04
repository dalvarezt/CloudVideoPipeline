import asyncio
import yaml
import logging
import sys, os
import io
import signal
import base64
import uuid
from datetime import datetime
from rstpGrabber import RTSP_grabber
from kafka_producer import KafkaProducer

threads = []
loop = asyncio.get_event_loop()
if 'LOGLEVEL' in os.environ:
    loglevel = os.environ['LOGLEVEL']
else:
    loglevel=logging.DEBUG
    
logging.basicConfig(level=loglevel)
logger = logging.getLogger("main")

def signal_handler(signal, frame):
    for t in threads:
        t.stop()
    loop.stop()
    sys.exit(0)

if __name__=="__main__":

    signal.signal(signal.SIGINT, signal_handler)


    try:
        kafka_config = yaml.safe_load(open("../conf/kafka_settings.yml"))
    except Exception as err:
        logger.error("Unable to obtain kafka configuration", err)
        sys.exit(os.EX_NOTFOUND)
    #kafka_config['bootstrap.servers']=kafka_config['bootstrap.servers'].split(",")
    producer = KafkaProducer(conf=kafka_config, topic_name="images")
    logger.debug("Producer connected")

    try:
        stream_settings = yaml.safe_load(open("../conf/stream_settings.yml"))
    except Exception as err:
        logger.error("Unable to load stream settings file", err)
        sys.exit(os.EX_NOTFOUND)
    
    for s in stream_settings['stream_sources']:
        rtsp = RTSP_grabber(s['source'], producer.produce, loop)
        threads.append(rtsp)
        rtsp.start()
    
    loop.run_forever()
