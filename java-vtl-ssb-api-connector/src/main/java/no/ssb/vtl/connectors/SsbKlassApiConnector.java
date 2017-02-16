package no.ssb.vtl.connectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Maps;
import no.ssb.vtl.connector.Connector;
import no.ssb.vtl.connector.ConnectorException;
import no.ssb.vtl.connector.NotFoundException;
import no.ssb.vtl.model.Component;
import no.ssb.vtl.model.DataStructure;
import no.ssb.vtl.model.Dataset;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;
import java.lang.String;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.*;
import static java.lang.String.format;
import static java.util.Arrays.*;


/**
 * A VTL connector that gets data from KLASS part of api.ssb.no.
 */
public class SsbKlassApiConnector implements Connector {

    private static final String SERVICE_URL = "http://data.ssb.no/api/klass/v1";
    private static final String DATA_PATH = "/classifications/{classificationId}/codes?from={codesFrom}";
    private static final String KLASS_DATE_PATTERN = "yyyy-MM-dd";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_VALID_FROM = "validFrom";
    private static final String FIELD_VALID_TO = "validTo";
    private static final String FIELD_NAME = "name";

    private static final DataStructure DATA_STRUCTURE =
            DataStructure.builder()
                    .put(FIELD_CODE, Component.Role.IDENTIFIER, String.class)
                    .put(FIELD_VALID_FROM, Component.Role.IDENTIFIER, Instant.class)
                    .put(FIELD_VALID_TO, Component.Role.IDENTIFIER, Instant.class)
                    .put(FIELD_NAME, Component.Role.MEASURE, String.class)
                    .build();

    private final UriTemplate dataTemplate;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;

    /*
        The list of available datasets:
        http://data.ssb.no/api/klass/v1/classifications/search?query=kommune

        Example dataset:
        http://data.ssb.no/api/klass/v1/classifications/131/codes?from=2016-01-01

     */

    public SsbKlassApiConnector(ObjectMapper mapper) {

        this.dataTemplate = new UriTemplate(SERVICE_URL + DATA_PATH);

        this.mapper = checkNotNull(mapper, "the mapper was null").copy();

        this.mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
        this.mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);

        this.mapper.registerModule(new GuavaModule());
        this.mapper.registerModule(new Jdk8Module());
        this.mapper.registerModule(new JavaTimeModule());

        SimpleModule module = new SimpleModule();
        module.addDeserializer(Map.class, new KlassDeserializer());
        this.mapper.registerModule(module);

        MappingJackson2HttpMessageConverter jacksonConverter;
        jacksonConverter = new MappingJackson2HttpMessageConverter(this.mapper);

        this.restTemplate = new RestTemplate(asList(
                jacksonConverter
        ));

    }

    /**
     * Gives access to the rest template to tests.
     */
    RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public boolean canHandle(String identifier) {
        return dataTemplate.matches(identifier);
    }

    public Dataset getDataset(String identifier) throws ConnectorException {

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);

            //http://data.ssb.no/api/klass/v1/classifications/131/codes?from=2016-01-01
            ResponseEntity<DatasetWrapper> exchange = restTemplate.exchange(
                    identifier,
                    HttpMethod.GET,
                    entity, DatasetWrapper.class);

            if (exchange.getBody() == null || exchange.getBody().getCodes().size() == 0) {
                throw new NotFoundException(format("empty dataset returned for the identifier %s", identifier));
            }

            List<Map<String, Object>> datasets = exchange.getBody().getCodes();

            return new Dataset() {
                @Override
                public DataStructure getDataStructure() {
                    return DATA_STRUCTURE;
                }

                @Override
                public Stream<Tuple> get() {
                    DataStructure dataStructure = getDataStructure();
                    Set<String> codeFields = dataStructure.keySet();
                    return datasets.stream()
                            .map(d -> Maps.filterKeys(d, codeFields::contains))
                            .map(d -> convertType(d))
                            .map(dataStructure::wrap);
                }
            };

        } catch (RestClientException rce) {
            throw new ConnectorException(
                    format("error when accessing the dataset with ids %s", identifier),
                    rce
            );
        }
    }

    private Map<String, Object> convertType(Map<String, Object> d) {
        Map<String, Object> copy = Maps.newLinkedHashMap(d);
        Map<String, Class<?>> types = DATA_STRUCTURE.getTypes();

        d.forEach((k, v) -> copy.put(k, mapper.convertValue(v, types.get(k))));

        return copy;
    }

    public Dataset putDataset(String identifier, Dataset dataset) throws ConnectorException {
        throw new ConnectorException("not supported");
    }

    static class DatasetWrapper {
        @JsonProperty
        private List<Map<String, Object>> codes;

        public DatasetWrapper() {
        }

        public List<Map<String, Object>> getCodes() {
            return codes;
        }

        public void setCodes(List<Map<String, Object>> codes) {
            this.codes = codes;
        }
    }

    private static class KlassDeserializer extends StdDeserializer<Map<String, Object>> {

        public KlassDeserializer() {
            this(null);
        }

        public KlassDeserializer(Class<?> vc) {
            super(vc);
        }

        @Override
        public Map<String, Object> deserialize(JsonParser jp, DeserializationContext ctxt)
                throws IOException {
            JsonNode node = jp.getCodec().readTree(jp);
            String code = node.get(FIELD_CODE).asText();
            String name = node.get(FIELD_NAME).asText();
            String validFromAsString = node.get(FIELD_VALID_FROM).asText();
            String validToAsString = node.get(FIELD_VALID_TO).asText();

            HashMap<String, Object> entry = Maps.newHashMap();
            entry.put(FIELD_CODE, code);
            entry.put(FIELD_NAME, name);
            entry.put(FIELD_VALID_FROM, parseKlassDate(validFromAsString));
            entry.put(FIELD_VALID_TO, parseKlassDate(validToAsString));

            return entry;
        }
    }

    public static Instant parseKlassDate(String input) throws JsonParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat(KLASS_DATE_PATTERN);
        if (input != null && !input.isEmpty() && !input.toLowerCase().equals("null")) {
            try {
                Date date = dateFormat.parse(input);
                return (date != null) ? date.toInstant() : null;
            } catch (ParseException e) {
                throw new JsonParseException(null, "Could not parse input to date. Data: "
                        + input + ", required format: " + KLASS_DATE_PATTERN);
            }
        }
        return null;
    }
}
