package com.krux.kafka.demos;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.krux.kafka.producer.KafkaProducer;
import com.krux.stdlib.KruxStdLib;

public class DemoProducer {
    
    private static final Logger LOG = LoggerFactory.getLogger( DemoProducer.class );

    public static void main( String[] args ) {
        
        // handle a couple custom cli-params
        OptionParser parser = new OptionParser();

        OptionSpec<String> topic = parser
                .accepts(
                        "topic",
                        "The topic to which messages will be sent")
                .withRequiredArg().ofType(String.class);
        OptionSpec<String> kafkaBrokers = parser
                .accepts(
                        "metadata.broker.list",
                        "This is for bootstrapping and the producer will only use it for getting metadata (topics, partitions and replicas). The socket connections for sending the actual data will be established based on the broker information returned in the metadata. The format is host1:port1,host2:port2, and the list can be a subset of brokers or a VIP pointing to a subset of brokers.")
                .withOptionalArg().ofType(String.class).defaultsTo("localhost:9092");
        OptionSpec<Integer> kafkaAckType = parser
                .accepts(
                        "request.required.acks",
                        "The type of ack the broker will return to the client.\n  0, which means that the producer never waits for an acknowledgement\n  1, which means that the producer gets an acknowledgement after the leader replica has received the data.\n  -1, which means that the producer gets an acknowledgement after all in-sync replicas have received the data.\nSee https://kafka.apache.org/documentation.html#producerconfigs")
                .withOptionalArg().ofType(Integer.class).defaultsTo(1);
        OptionSpec<String> producerType = parser.accepts("producer.type", "'sync' or 'async'").withOptionalArg()
                .ofType(String.class).defaultsTo("async");

        OptionSpec<Integer> kafkaRequestTimeoutMs = parser
                .accepts(
                        "request.timeout.ms",
                        "The amount of time the broker will wait trying to meet the request.required.acks requirement before sending back an error to the client.")
                .withOptionalArg().ofType(Integer.class).defaultsTo(10000);
        OptionSpec<String> kafkaCompressionType = parser
                .accepts(
                        "compression.codec",
                        "This parameter allows you to specify the compression codec for all data generated by this producer. Valid values are \"none\", \"gzip\" and \"snappy\".")
                .withOptionalArg().ofType(String.class).defaultsTo("none");
        OptionSpec<Integer> messageSendMaxRetries = parser
                .accepts(
                        "message.send.max.retries",
                        "This property will cause the producer to automatically retry a failed send request. This property specifies the number of retries when such failures occur. Note that setting a non-zero value here can lead to duplicates in the case of network errors that cause a message to be sent but the acknowledgement to be lost.")
                .withOptionalArg().ofType(Integer.class).defaultsTo(3);
        OptionSpec<Integer> retryBackoffMs = parser
                .accepts(
                        "retry.backoff.ms",
                        "Before each retry, the producer refreshes the metadata of relevant topics to see if a new leader has been elected. Since leader election takes a bit of time, this property specifies the amount of time that the producer waits before refreshing the metadata.")
                .withOptionalArg().ofType(Integer.class).defaultsTo(100);
        OptionSpec<Integer> queueBufferingMaxMs = parser
                .accepts(
                        "queue.buffering.max.ms",
                        "Maximum time to buffer data when using async mode. For example a setting of 100 will try to batch together 100ms of messages to send at once. This will improve throughput but adds message delivery latency due to the buffering.")
                .withOptionalArg().ofType(Integer.class).defaultsTo(5000);
        OptionSpec<Integer> queueBufferingMaxMessages = parser
                .accepts(
                        "queue.buffering.max.messages",
                        "The maximum number of unsent messages that can be queued up the producer when using async mode before either the producer must be blocked or data must be dropped.")
                .withOptionalArg().ofType(Integer.class).defaultsTo(10000);
        OptionSpec<Integer> queueEnqueTimeoutMs = parser
                .accepts(
                        "queue.enqueue.timeout.ms",
                        "The amount of time to block before dropping messages when running in async mode and the buffer has reached queue.buffering.max.messages. If set to 0 events will be enqueued immediately or dropped if the queue is full (the producer send call will never block). If set to -1 the producer will block indefinitely and never willingly drop a send.")
                .withOptionalArg().ofType(Integer.class).defaultsTo(-1);
        OptionSpec<Integer> batchNumMessages = parser
                .accepts(
                        "batch.num.messages",
                        "The number of messages to send in one batch when using async mode. The producer will wait until either this number of messages are ready to send or queue.buffer.max.ms is reached.")
                .withOptionalArg().ofType(Integer.class).defaultsTo(200);
        OptionSpec<String> clientId = parser
                .accepts(
                        "client.id",
                        "The client id is a user-specified string sent in each request to help trace calls. It should logically identify the application making the request.")
                .withOptionalArg().ofType(String.class).defaultsTo("");
        OptionSpec<Integer> sendBufferBytes = parser.accepts("send.buffer.bytes", "Socket write buffer size")
                .withOptionalArg().ofType(Integer.class).defaultsTo(100 * 1024);
        
        OptionSpec<Integer> numOfMessagesToSend = parser
                .accepts(
                        "num-of-messages-to-send",
                        "Total number of messages to send.")
                .withRequiredArg().ofType(Integer.class);

        // give parser to KruxStdLib so it can add our params to the reserved
        // list
        KruxStdLib.setOptionParser(parser);
        StringBuilder desc = new StringBuilder();
        desc.append("\nKrux Kafka Stream Listener\n");
        desc.append("**************************\n");
        desc.append("Will pass incoming eol-delimitted messages on tcp streams to mapped Kafka topics.\n");
        OptionSet options = KruxStdLib.initialize(desc.toString(), args);
        
        //make sure we have what we need
        if ( !options.has( topic ) || !options.has(  kafkaBrokers ) || !options.has(  numOfMessagesToSend )) {
            LOG.error( "--topic, --metadata.broker.list and --num-of-messages-to-send are required cl params. Rerun with -h for more info." );
            System.exit( -1 );
        }

        // parse the configured port -> topic mappings, put in global hashmap
        Map<OptionSpec<?>, List<?>> optionMap = options.asMap();
        String topicName = options.valueOf(  topic  );
        
        Properties producerProps = new Properties();
        // these are picked up by the KafkaProducer class
        producerProps.setProperty("topic", topicName);
        producerProps.setProperty("metadata.broker.list", (String) optionMap.get(kafkaBrokers).get(0));
        producerProps.setProperty("request.required.acks", String.valueOf((Integer) optionMap.get(kafkaAckType).get(0)));
        producerProps.setProperty("producer.type", (String) optionMap.get(producerType).get(0));

        producerProps.setProperty("request.timeout.ms", String.valueOf((Integer) optionMap.get(kafkaRequestTimeoutMs).get(0)));
        producerProps.setProperty("compression.codec", (String) optionMap.get(kafkaCompressionType).get(0));
        producerProps.setProperty("message.send.max.retries", String.valueOf((Integer) optionMap.get(messageSendMaxRetries).get(0)));
        producerProps.setProperty("retry.backoff.ms", String.valueOf((Integer) optionMap.get(retryBackoffMs).get(0)));
        producerProps.setProperty("queue.buffering.max.ms", String.valueOf((Integer) optionMap.get(queueBufferingMaxMs).get(0)));
        producerProps.setProperty("queue.buffering.max.messages",
                String.valueOf((Integer) optionMap.get(queueBufferingMaxMessages).get(0)));
        producerProps.setProperty("queue.enqueue.timeout.ms", String.valueOf((Integer) optionMap.get(queueEnqueTimeoutMs).get(0)));
        producerProps.setProperty("batch.num.messages", String.valueOf(options.valueOf(batchNumMessages)));
        producerProps.setProperty("client.id", options.valueOf(clientId));
        producerProps.setProperty("send.buffer.bytes", String.valueOf((Integer) optionMap.get(sendBufferBytes).get(0)));
        
        KafkaProducer producer = new KafkaProducer( producerProps, topicName );

        Random r = new Random();
        int j = Math.abs( r.nextInt() );
        String runId = Integer.toHexString( j );
        int numMessages = options.valueOf((  numOfMessagesToSend ));
        
        LOG.info( "Sending " +  numMessages + " messages." );
        int count = 0;
        for (int i = 0; i < numMessages; i++ ) {
            producer.send( runId + ": " + String.valueOf( i ) );
            count++;
        }
        LOG.info( "Sent " +  count + " messages." );
        
        System.exit( 0 );
    }

}
