package net.enthusia.staff.paper.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MessageConfigurationLoader {
    private static final String ROOT_PATH = "root";
    private static final Set<String> ROOT_FIELDS = Set.of("schema-version", "messages");

    private final ObjectMapper yaml;

    public MessageConfigurationLoader() {
        YAMLFactory factory = new YAMLFactory();
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        yaml = new ObjectMapper(factory);
    }

    public MessageConfigurationSnapshot load(Path file) {
        if (file == null) {
            throw invalid("messages.yml path must be present");
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return load(reader, file.getFileName().toString());
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to read messages.yml", exception);
        }
    }

    public MessageConfigurationSnapshot load(InputStream input, String sourceName) {
        if (input == null) {
            throw invalid(sourceName + " was not found");
        }
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return load(reader, sourceName);
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to read " + sourceName, exception);
        }
    }

    MessageConfigurationSnapshot load(Reader reader, String sourceName) {
        try {
            return parse(reader);
        } catch (IOException exception) {
            throw new ConfigurationValidationException("Unable to parse " + sourceName, exception);
        } catch (ConfigurationValidationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ConfigurationValidationException(
                    "Invalid messages.yml: " + exception.getMessage(),
                    exception
            );
        }
    }

    private MessageConfigurationSnapshot parse(Reader reader) throws IOException {
        JsonNode root = yaml.readTree(reader);
        requireObject(root, ROOT_PATH);
        rejectUnknown(root, ROOT_FIELDS, ROOT_PATH);

        int schemaVersion = requiredSchemaVersion(root);
        JsonNode messages = required(root, "messages", ROOT_PATH);
        requireObject(messages, ROOT_PATH + ".messages");

        return new MessageConfigurationSnapshot(
                schemaVersion,
                new MessageCatalog(parseTemplates(messages, schemaVersion))
        );
    }

    private static int requiredSchemaVersion(JsonNode root) {
        int schemaVersion = integer(root, "schema-version", ROOT_PATH);
        if (schemaVersion < MessageConfigurationSnapshot.EARLIEST_SUPPORTED_SCHEMA_VERSION
                || schemaVersion > MessageConfigurationSnapshot.CURRENT_SCHEMA_VERSION) {
            throw invalid(ROOT_PATH + ".schema-version must be between "
                    + MessageConfigurationSnapshot.EARLIEST_SUPPORTED_SCHEMA_VERSION
                    + " and " + MessageConfigurationSnapshot.CURRENT_SCHEMA_VERSION);
        }
        return schemaVersion;
    }

    private static EnumMap<MessageKey, String> parseTemplates(JsonNode messages, int schemaVersion) {
        Map<String, String> flattened = new LinkedHashMap<>();
        flatten(messages, "", flattened, ROOT_PATH + ".messages");

        EnumMap<MessageKey, String> templates = new EnumMap<>(MessageKey.class);
        for (Map.Entry<String, String> entry : flattened.entrySet()) {
            MessageKey key = MessageKey.fromPath(entry.getKey());
            if (key == null) {
                throw invalid(ROOT_PATH + ".messages contains unknown key " + entry.getKey());
            }
            if (key.introducedSchemaVersion() > schemaVersion) {
                throw invalid(ROOT_PATH + ".messages." + key.path()
                        + " requires schema-version " + key.introducedSchemaVersion());
            }
            templates.put(key, entry.getValue());
        }
        completeTemplates(templates, schemaVersion);
        return templates;
    }

    private static void completeTemplates(EnumMap<MessageKey, String> templates, int schemaVersion) {
        for (MessageKey key : MessageKey.values()) {
            if (templates.containsKey(key)) {
                continue;
            }
            if (key.introducedSchemaVersion() > schemaVersion) {
                templates.put(key, key.defaultText());
            } else {
                throw invalid(ROOT_PATH + ".messages." + key.path() + " is required");
            }
        }
    }

    private static void flatten(
            JsonNode node,
            String prefix,
            Map<String, String> flattened,
            String path
    ) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                flatten(value, key, flattened, path + "." + entry.getKey());
            } else if (value.isTextual() && !value.textValue().isBlank()) {
                flattened.put(key, value.textValue());
            } else {
                throw invalid(path + "." + entry.getKey() + " must be a non-blank string or object");
            }
        }
    }

    private static JsonNode required(JsonNode parent, String field, String path) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull()) {
            throw invalid(path + "." + field + " is required");
        }
        return value;
    }

    private static int integer(JsonNode parent, String field, String path) {
        JsonNode value = required(parent, field, path);
        if (!value.canConvertToInt()) {
            throw invalid(path + "." + field + " must be an integer");
        }
        return value.intValue();
    }

    private static void rejectUnknown(JsonNode node, Set<String> allowed, String path) {
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw invalid(path + " contains unknown field " + field);
            }
        }
    }

    private static void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw invalid(path + " must be an object");
        }
    }

    private static ConfigurationValidationException invalid(String message) {
        return new ConfigurationValidationException(message);
    }
}
