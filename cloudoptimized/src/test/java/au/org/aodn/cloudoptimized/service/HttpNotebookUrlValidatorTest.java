package au.org.aodn.cloudoptimized.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * No test here sends a request outside of this machine, the network cases use a mock server or a local server.
 */
class HttpNotebookUrlValidatorTest {

    private static final String URL = "https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/test_notebook.ipynb";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private HttpServer localServer;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @AfterEach
    void tearDown() {
        if (localServer != null) {
            localServer.stop(0);
        }
    }

    @Test
    void ok200IsValid() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(HttpStatus.OK));

        NotebookUrlCheck check = new HttpNotebookUrlValidator(restTemplate, true).check(URL);

        assertTrue(check.valid());
        assertNull(check.reason());
        server.verify();
    }

    @Test
    void urlIsTrimmed() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.OK));

        assertTrue(new HttpNotebookUrlValidator(restTemplate, true).check("  " + URL + "\n").valid());
        server.verify();
    }

    @Test
    void notOkStatusIsInvalid() {
        // Rule is any status other than 2xx drop the link, including throttle and server error
        for (HttpStatus status : new HttpStatus[]{HttpStatus.NOT_FOUND, HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.METHOD_NOT_ALLOWED}) {
            server.reset();
            server.expect(requestTo(URL)).andRespond(withStatus(status));

            NotebookUrlCheck check = new HttpNotebookUrlValidator(restTemplate, true).check(URL);

            assertFalse(check.valid(), status.toString());
            assertEquals("HTTP " + status.value(), check.reason());
            server.verify();
        }
    }

    @Test
    void timeoutAndIoErrorAreInvalid() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new SocketTimeoutException("Read timed out");
        });

        NotebookUrlCheck check = new HttpNotebookUrlValidator(restTemplate, true).check(URL);

        assertFalse(check.valid());
        assertEquals("request failed: ResourceAccessException", check.reason());
        server.verify();
    }

    @Test
    void notAnHttpUrlIsInvalidWithoutRequest() {
        // No expectation set, so the mock server fails the test if a request is sent
        HttpNotebookUrlValidator validator = new HttpNotebookUrlValidator(restTemplate, true);

        for (String value : new String[]{
                null,
                "",
                "   ",
                "FILL UP MANUALLY - CHECK DOCUMENTATION",
                "ftp://example.org/notebook.ipynb",
                "file:///etc/passwd",
                "github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/a.ipynb",
                "https:///no-host.ipynb"
        }) {
            NotebookUrlCheck check = validator.check(value);
            assertFalse(check.valid(), "value: " + value);
            assertNotNull(check.reason(), "value: " + value);
        }
        server.verify();
    }

    @Test
    void checkDisabledOnlyCheckTheScheme() {
        // No expectation set, so the mock server fails the test if a request is sent
        HttpNotebookUrlValidator validator = new HttpNotebookUrlValidator(restTemplate, false);

        assertTrue(validator.check(URL).valid());
        assertFalse(validator.check("FILL UP MANUALLY - CHECK DOCUMENTATION").valid());
        assertFalse(validator.check("ftp://example.org/notebook.ipynb").valid());
        server.verify();
    }

    @Test
    void requestHasNoCredentials() {
        // The notebook host is external, it must never see the data access service credentials
        server.expect(requestTo(URL))
                .andExpect(headerDoesNotExist("X-API-Key"))
                .andExpect(headerDoesNotExist("x-internal-das-header-secret"))
                .andRespond(withStatus(HttpStatus.OK));

        assertTrue(new HttpNotebookUrlValidator(restTemplate, true).check(URL).valid());
        server.verify();
    }

    @Test
    void createdRestTemplateHasNoInterceptor() {
        // The interceptor of the data access service is what adds the credentials
        assertTrue(HttpNotebookUrlValidator.createRestTemplate(1000).getInterceptors().isEmpty());
    }

    @Test
    void createdRestTemplateFollowsRedirectOfHead() throws IOException {
        localServer = startLocalServer();
        localServer.createContext("/old", exchange -> {
            exchange.getResponseHeaders().add("Location", "/new");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        localServer.createContext("/new", exchange -> {
            exchange.sendResponseHeaders(exchange.getRequestMethod().equals("HEAD") ? 200 : 405, -1);
            exchange.close();
        });
        localServer.createContext("/old-to-missing", exchange -> {
            exchange.getResponseHeaders().add("Location", "/missing");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        localServer.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        localServer.start();

        RestTemplate created = HttpNotebookUrlValidator.createRestTemplate(2000);

        // The validator only requests the allowed notebook hosts, so test the rest template it uses directly
        assertEquals(HttpStatus.OK, created.exchange(URI.create(baseUrl() + "/old"), HttpMethod.HEAD, null, Void.class).getStatusCode());

        HttpClientErrorException missing = assertThrows(HttpClientErrorException.class,
                () -> created.exchange(URI.create(baseUrl() + "/old-to-missing"), HttpMethod.HEAD, null, Void.class));
        assertEquals(404, missing.getStatusCode().value());
    }

    @Test
    void createdRestTemplateHasReadTimeout() throws IOException {
        localServer = startLocalServer();
        localServer.createContext("/slow", exchange -> {
            try {
                Thread.sleep(3000);
                exchange.sendResponseHeaders(200, -1);
            }
            catch (InterruptedException | IOException e) {
                // The client gave up or the server stopped, fine
            }
            finally {
                exchange.close();
            }
        });
        localServer.start();

        RestTemplate created = HttpNotebookUrlValidator.createRestTemplate(300);

        long start = System.currentTimeMillis();
        assertThrows(ResourceAccessException.class,
                () -> created.exchange(URI.create(baseUrl() + "/slow"), HttpMethod.HEAD, null, Void.class));
        assertTrue(System.currentTimeMillis() - start < 2500, "Should give up on the timeout");
    }

    @Test
    void onlyGithubNotebookUrlIsRequested() {
        // No expectation set, so the mock server fails the test if a request is sent
        HttpNotebookUrlValidator validator = new HttpNotebookUrlValidator(restTemplate, true);

        for (String value : new String[]{
                // Not the github host
                "http://169.254.169.254/latest/meta-data/",
                "http://localhost:8080/manage/env",
                "http://localhost:8080/a.ipynb",
                "https://github.com.evil.example/aodn/a.ipynb",
                "https://evil.example/https://github.com/aodn/a.ipynb",
                "http://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/a.ipynb",
                "https://nbviewer.org/github/aodn/aodn_cloud_optimised/blob/main/notebooks/a.ipynb",
                // The github host, but not a notebook file
                "https://github.com/aodn/aodn_cloud_optimised",
                "https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/",
                "https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/a.ipynb.html",
                "https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/a.ipyn",
                "https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/a?x=.ipynb",
                "https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/a.ipynb?raw=1",
                "https://github.com/aodn/aodn_cloud_optimised/blob/main/notebooks/a.ipynb#cell"
        }) {
            NotebookUrlCheck check = validator.check(value);
            assertFalse(check.valid(), "value: " + value);
            assertEquals("not a cloud-optimised github notebook url", check.reason(), "value: " + value);
        }
        server.verify();
    }

    @Test
    void notebookOfOtherRepoIsNotAllowed() {
        // The prefix is the notebooks folder of aodn_cloud_optimised, other repos are not notebooks of a CO dataset
        HttpNotebookUrlValidator validator = new HttpNotebookUrlValidator(restTemplate, false);

        assertTrue(validator.check(URL).valid());
        assertFalse(validator.check("https://github.com/other-org/repo/blob/main/a.ipynb").valid());
        assertFalse(validator.check("https://github.com/aodnx/repo/blob/main/a.ipynb").valid());
        assertFalse(validator.check("https://github.com/aodn/aodn_cloud_optimised/blob/dev/notebooks/a.ipynb").valid());
        server.verify();
    }

    @Test
    void notGithubNotebookUrlEvenWhenCheckDisabled() {
        // The link goes to the index and the portal, so keep only github notebooks there too
        HttpNotebookUrlValidator validator = new HttpNotebookUrlValidator(restTemplate, false);

        assertFalse(validator.check("http://localhost:8080/a.ipynb").valid());
        assertFalse(validator.check("https://github.com/aodn/aodn_cloud_optimised").valid());
        server.verify();
    }

    private HttpServer startLocalServer() throws IOException {
        // Port 0 let the system pick a free port, and only bind to loopback
        return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    }

    private String baseUrl() {
        return "http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + localServer.getAddress().getPort();
    }
}
