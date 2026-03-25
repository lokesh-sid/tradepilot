package tradingbot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Abstract base class providing common HTTP testing utilities.
 * Contains generic HTTP request methods used by both validation and integration tests.
 */
public abstract class AbstractHttpTest {

    @Autowired(required = false)
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    // ========== GENERIC HTTP METHODS ==========

    /**
     * Performs a generic HTTP request with JSON content type.
     * @param method HTTP method (GET, POST, PUT, DELETE)
     * @param url Request URL (can include path variables)
     * @param body Request body object (will be JSON serialized, null for GET/DELETE)
     * @return ResultActions for further assertions
     */
    protected ResultActions performRequest(HttpMethod method, String url, Object body) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = createRequestBuilder(method, url);

        if (body != null && (method == HttpMethod.POST || method == HttpMethod.PUT)) {
            requestBuilder
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        }

        return mockMvc.perform(requestBuilder);
    }

    /**
     * Performs a generic HTTP request and returns the result.
     * @param method HTTP method
     * @param url Request URL
     * @param body Request body object
     * @return MvcResult containing the response
     */
    protected MvcResult performRequestAndReturn(HttpMethod method, String url, Object body) throws Exception {
        return performRequest(method, url, body).andReturn();
    }

    /**
     * Performs a GET request.
     * @param url Request URL
     * @return ResultActions for assertions
     */
    protected ResultActions performGet(String url) throws Exception {
        return performRequest(HttpMethod.GET, url, null);
    }

    /**
     * Performs a POST request with JSON body.
     * @param url Request URL
     * @param body Request body object
     * @return ResultActions for assertions
     */
    protected ResultActions performPost(String url, Object body) throws Exception {
        return performRequest(HttpMethod.POST, url, body);
    }

    /**
     * Performs a POST request with raw JSON string body.
     * @param url Request URL
     * @param jsonBody Raw JSON string body (can be null for empty body)
     * @return ResultActions for assertions
     */
    protected ResultActions performPost(String url, String jsonBody) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = post(url)
                .contentType(MediaType.APPLICATION_JSON);

        if (jsonBody != null) {
            requestBuilder.content(jsonBody);
        }

        return mockMvc.perform(requestBuilder);
    }

    /**
     * Performs a PUT request with JSON body.
     * @param url Request URL
     * @param body Request body object
     * @return ResultActions for assertions
     */
    protected ResultActions performPut(String url, Object body) throws Exception {
        return performRequest(HttpMethod.PUT, url, body);
    }

    /**
     * Performs a PUT request with raw JSON string body.
     * @param url Request URL
     * @param jsonBody Raw JSON string body (can be null for empty body)
     * @return ResultActions for assertions
     */
    protected ResultActions performPut(String url, String jsonBody) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = put(url)
                .contentType(MediaType.APPLICATION_JSON);

        if (jsonBody != null) {
            requestBuilder.content(jsonBody);
        }

        return mockMvc.perform(requestBuilder);
    }

    /**
     * Performs a DELETE request.
     * @param url Request URL
     * @return ResultActions for assertions
     */
    protected ResultActions performDelete(String url) throws Exception {
        return performRequest(HttpMethod.DELETE, url, null);
    }

    /**
     * Creates the appropriate MockHttpServletRequestBuilder for the HTTP method.
     */
    private MockHttpServletRequestBuilder createRequestBuilder(HttpMethod method, String url) {
        if (method == HttpMethod.GET) {
            return get(url);
        } else if (method == HttpMethod.POST) {
            return post(url);
        } else if (method == HttpMethod.PUT) {
            return put(url);
        } else if (method == HttpMethod.DELETE) {
            return delete(url);
        } else {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
    }
}