package water.clustering.api;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class HttpResponses {
    public static final String MIME_TYPE_TEXT_PLAIN = "text/plain";
    public static final String MIME_TYPE_JSON = "application/json";
    public static final String GET_METHOD = "GET";
    public static final String POST_METHOD = "POST";

    public static void newResponseCodeOnlyResponse(HttpExchange httpExchange, int responseCode) throws IOException {
        httpExchange.getResponseHeaders().set("Content-Type", MIME_TYPE_TEXT_PLAIN);
        httpExchange.sendResponseHeaders(responseCode, -1);
        httpExchange.close();
    }

    public static void newFixedLengthResponse(HttpExchange httpExchange, int responseCode, String mimeType, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        httpExchange.getResponseHeaders().set("Content-Type", mimeType);
        httpExchange.sendResponseHeaders(responseCode, responseBytes.length);
        OutputStream os = httpExchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }
}
