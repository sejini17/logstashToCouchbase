import com.couchbase.client.deps.com.fasterxml.jackson.databind.JsonNode;
import com.couchbase.client.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.streams.KafkaStreams;
import serde.KeyAvroSerde;
import serde.ValueAvroSerde;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.apache.kafka.streams.kstream.Predicate;
import org.apache.kafka.streams.kstream.ValueMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Properties;

public class logstashToCouchbase {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//    private static final String SERVER_IP = "211.219.160.150";
    private static final String SERVER_IP = "localhost";

    public static void main(String[] args) throws InterruptedException, SQLException {
        /**
         * CREATE TABLE `logstash_system_auth` (
         `timestamp` datetime NOT NULL,
         `hostname` varchar(255) NOT NULL,
         `source` varchar(255) DEFAULT NULL,
         `message` text,
         `offset` int(11) NOT NULL,
         `prospector_type` varchar(10) DEFAULT NULL,
         `fileset_mod` varchar(10) DEFAULT NULL,
         `fileset_name` varchar(10) DEFAULT NULL,
         PRIMARY KEY (`timestamp`)
         );
         */
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("Failed to load MySQL JDBC driver");
        }
        Connection connection = DriverManager
                .getConnection("jdbc:mysql://"+SERVER_IP+":3306/test", "root", "dpsxndpa");
        final PreparedStatement insertRecord = connection.prepareStatement(
//                "INSERT INTO beers (id, brewery_id, name, description, category, style, abv, ibu, updated_at)" +
//                        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)" +
//                        " ON DUPLICATE KEY UPDATE" +
//                        " brewery_id=VALUES(brewery_id), name=VALUES(name), description=VALUES(description)," +
//                        " category=VALUES(category), style=VALUES(style), abv=VALUES(abv)," +
//                        " ibu=VALUES(ibu), updated_at=VALUES(updated_at)"

                "INSERT INTO `logstash_system_auth`\n" +
                "    (`timestamp`,\n" +
                "    `hostname`,\n" +
                "    `source`,\n" +
                "    `message`,\n" +

                "    `offset`,\n" +
                "    `prospector_type`,\n" +
                "    `fileset_mod`,\n" +
                "    `fileset_name`)\n" +
                "VALUES (?, ?, ?, ?,    ?, ?, ?, ?);"
            );
System.out.println(insertRecord.toString());

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streams-test");

        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, SERVER_IP + ":9092");
        props.put(StreamsConfig.ZOOKEEPER_CONNECT_CONFIG, SERVER_IP + ":2181");
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://"+SERVER_IP+":8082");

//        Caused by: org.apache.kafka.common.errors.SerializationException: Error deserializing Avro message for id -1
//        Caused by: org.apache.kafka.common.errors.SerializationException: Unknown magic byte!

        props.put(StreamsConfig.KEY_SERDE_CLASS_CONFIG, KeyAvroSerde.class);
        props.put(StreamsConfig.VALUE_SERDE_CLASS_CONFIG, ValueAvroSerde.class);
//
//        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
//        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        props.put("key.deserializer", io.confluent.kafka.serializers.KafkaAvroDeserializer.class);
        props.put("value.deserializer", io.confluent.kafka.serializers.KafkaAvroDeserializer.class);
        //io.confluent.kafka.serializers.KafkaAvroSerializer.class.getName()

        props.put("key.serde", KeyAvroSerde.class);
        props.put("value.serde", ValueAvroSerde.class);

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

System.out.println(props.toString());
        KStreamBuilder builder = new KStreamBuilder();

        KStream<String, GenericRecord> source = builder.stream("test_logstash");

        KStream<String, JsonNode>[] documents = source
                .mapValues(new ValueMapper<GenericRecord, JsonNode>() {
//                .mapValues(new ValueMapper<GenericRecord, JsonNode>() {
                    @Override
                    public JsonNode apply(GenericRecord value) {
                        ByteBuffer buf = (ByteBuffer) value.get("content");
                        try {
                            JsonNode doc = MAPPER.readTree(buf.array());
System.out.println(doc.toString());
                            return doc;
                        } catch (IOException e) {
                            return null;
                        }
                    }
                })
                .branch(
                        new Predicate<String, JsonNode>() {
                            @Override
                            public boolean test(String key, JsonNode value) {
                                return true;
//                                return "beer".equals(value.get("type").asText()) &&
//                                        value.has("brewery_id") &&
//                                        value.has("name") &&
//                                        value.has("description") &&
//                                        value.has("category") &&
//                                        value.has("style") &&
//                                        value.has("abv") &&
//                                        value.has("ibu") &&
//                                        value.has("updated");
                            }
                        }
                );
        documents[0].foreach(new ForeachAction<String, JsonNode>() {
            @Override
            public void apply(String key, JsonNode value) {
                try {
                    insertRecord.setDate(1, new Date(
                            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz")
                                    .parse(key).getTime())
                    );
                    insertRecord.setString(2, value.get("hostname").asText());
                    insertRecord.setString(3, value.get("source").asText());
                    insertRecord.setString(4, value.get("message").asText());

                    insertRecord.setInt(5, new Integer( value.get("offset").asText() ));

                    insertRecord.setString(6, value.get("prospector_type").asText());
                    insertRecord.setString(7, value.get("fileset_mod").asText());
                    insertRecord.setString(8, value.get("fileset_name").asText());

                    insertRecord.execute();

System.out.println(insertRecord.toString());
                } catch (SQLException e) {
                    System.err.println("Failed to insert record: " + key + ". " + e);
                } catch (ParseException e) {
                    System.err.println("Failed to insert record: " + key + ". " + e);
                }
            }
        });

        final KafkaStreams streams = new KafkaStreams(builder, props);
        streams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                streams.close();
            }
        }));
    }
}
