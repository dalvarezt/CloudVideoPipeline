"""
 Copyright 2015-2018 IBM

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 Licensed Materials - Property of IBM
 © Copyright IBM Corp. 2015-2018
"""
import asyncio
import sched, time
import sys,os
import logging
from confluent_kafka import Producer

class KafkaProducer(object):
    RETRY_QUEUE_LENGTH = 18554
    def __init__(self, conf, topic_name):
        self.topic_name = topic_name
        self.producer = Producer(conf)
        self.running = True
        self.scheduler = sched.scheduler(time.time, time.sleep)
        self.logger = logging.getLogger("Producer")
    def stop(self):
        self.running = False

    def on_delivery(self, err, msg):
        if err:
            self.logger.warn('Delivery report: Failed sending message {0}'.format(msg.value()),err)
            self.schedule_retry(msg.value(), msg.key())
        else:
            self.logger.debug('Message produced, offset: {0}'.format(msg.offset()))

    @asyncio.coroutine
    def produce(self, msg, key, callback=None):
        if callback is None:
            callback = self.on_delivery
        self.logger.debug("sending message: %d" % len(msg))
        try:
            self.producer.produce(self.topic_name, msg, key, -1, self.on_delivery)
            asyncio.get_event_loop().create_task(self.async_poll())
        except Exception as err:
            self.logger.warn("Error sendig message with key {0}. Retrying".format(key), err)
            self.schedule_retry(msg, key, callback)
        yield #from asyncio.sleep(2)
    
    @asyncio.coroutine
    def async_poll(self):
        self.producer.poll(0.0)
        yield from asyncio.sleep(2)

    
    def schedule_retry(self, msg, key, callback=None):
        if len(self.scheduler.queue)<self.RETRY_QUEUE_LENGTH:
            self.logger.warn("Scheduling retry for message with key {0}".format(key))
            self.scheduler.enter(delay=5, priority=1, argument=self.produce, kwargs=[msg, key, callback])
            self.logger.debug("Retry queue length: %d" % len(self.scheduler.queue))
        else:
            self.logger.error("Retry queue too long. Giving up.")
            sys.exit(os.EX_SOFTWARE)

    def __del__(self):
        self.producer.flush()


