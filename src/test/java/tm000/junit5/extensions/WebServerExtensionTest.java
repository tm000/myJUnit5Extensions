package tm000.junit5.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;

public class WebServerExtensionTest {
    @RegisterExtension
    static WebServerExtension server = WebServerExtension.builder()
        .enableSecurity(false)
        .build();

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        System.out.println(testInfo.getDisplayName() + " start --------------------------------------------------------------------------------------");
    }

    @Test
    @SimpleHttpResponse(value = "<html><head><title>Test Http Server</title></head><body><h1>Hello world!</h1></body></html>", keepalive = true)
    @SimpleHttpResponse(status = 400, contentType = "text/plain", keepalive = true)
    @SimpleHttpResponse(status = 500, contentType = "text/plain", keepalive = false)
    void requestUsingNioSocketChannel() throws IOException, InterruptedException {
        InetSocketAddress hA = new InetSocketAddress("localhost", 9000);
        SocketChannel client = SocketChannel.open(hA);
        client.configureBlocking(false);
        while (!client.finishConnect()) {
            // System.out.println("still connecting");
        }
        // The Client is sending messages to server...
        String [] msg = new String [] {"Hello.", "World", "Bye Bye"};
        for (int i = 0; i < msg.length; i++) {
            ByteBuffer myAppData = ByteBuffer.wrap(msg[i].getBytes());
            while (myAppData.hasRemaining()) {
                client.write(myAppData);
            }

            StringBuilder response = new StringBuilder();
            boolean readStart = false;
            ByteBuffer peerNetData = ByteBuffer.allocate(16*1024);
            int byteReads = 0;
            while (!readStart) {
                while ((byteReads = client.read(peerNetData)) > 0) {
                    readStart = true;
                    peerNetData.flip();
                    response.append(new String(peerNetData.array(), 0, byteReads).trim());
                    peerNetData.clear();
                }
                if (!readStart) {
                    Thread.sleep(30);
                }
            }
            // check response
            switch (i) {
                case 0:
                    assertEquals("HTTP/1.1 200 OK\r\nContent-Length: 91\r\nConnection: keep-alive\r\nContent-Type: text/html\r\n\r\n<html><head><title>Test Http Server</title></head><body><h1>Hello world!</h1></body></html>", response.toString());
                    break;
                case 1:
                    assertEquals("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: keep-alive\r\nContent-Type: text/plain", response.toString());
                    break;
                default:
                    assertEquals("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\nContent-Type: text/plain", response.toString());
            }
        }
        client.close();
        assertEquals("Hello.", server.getRequests().get(0));
        assertEquals("World", server.getRequests().get(1));
        assertEquals("Bye Bye", server.getRequests().get(2));
    }

    @Test
    @SimpleHttpResponse(value="<html><head><title>Test Http Server</title></head><body><h1>Hello world!</h1></body></html>", keepalive = true)
    @SimpleHttpResponse(status=400, contentType = "text/plain")
    @SimpleHttpResponse(status=500, contentType = "text/plain", keepalive = false)
    void requestUsingApacheHttpClient() throws Exception {
        try (CloseableHttpClient httpclient = HttpClients.createDefault();) {
            HttpGet httpget = new HttpGet("http://localhost:9000");
            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                @Override
                public String handleResponse(
                        final HttpResponse response) throws IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity) : null;
                    } else {
                        return "statusCode=" + status;
                    }
                }
            };
            String responseBody = httpclient.execute(httpget, responseHandler);
            assertEquals("<html><head><title>Test Http Server</title></head><body><h1>Hello world!</h1></body></html>", responseBody);

            httpget = new HttpGet("http://localhost:9000/static/img");
            responseBody = httpclient.execute(httpget, responseHandler);
            assertEquals("statusCode=400", responseBody);

            httpget = new HttpGet("http://localhost:9000/files");
            responseBody = httpclient.execute(httpget, responseHandler);
            assertEquals("statusCode=500", responseBody);
        }
    }

    @Test
    @WebServerResponse("HTTP/1.1 200 OK\r\nDate: Mon, 19 Jul 2004 16:18:20 GMT\r\nServer: Apache\r\nLast-Modified: Sat, 10 Jul 2004 17:29:19 GMT\r\nETag: \"1d0325-2470-40f0276f\"\r\nAccept-Ranges: bytes\r\nContent-Length: 91\r\nConnection: close\r\nContent-Type: text/html\r\n\r\n<html><head><title>Test Http Server</title></head><body><h1>Hello world!</h1></body></html>")
    void sampleWebServerResponse() throws IOException, InterruptedException {
        SocketChannel client = SocketChannel.open(new InetSocketAddress("localhost", 9000));
        client.configureBlocking(false);
        while (!client.finishConnect()) {
            // System.out.println("still connecting");
        }

        // write
        String message = "hello - from client [" + Thread.currentThread().getName() + "]";
        ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
        while (buffer.hasRemaining()) {
            client.write(buffer);
        }

        // read
        ByteBuffer inBuf = ByteBuffer.allocate(1024);
        int waitCnt = 1000;
        int bytesRead = 0;
        while ((bytesRead = client.read(inBuf)) > 0 | waitCnt > 0) {
            if (bytesRead > 0) {
                System.out.printf("[%s]:\t%s\n", Thread.currentThread().getName(), new String(inBuf.array(), 0, bytesRead, StandardCharsets.UTF_8));
                inBuf.clear();
                waitCnt = 0;
            } else {
                waitCnt--;
                Thread.sleep(10);
            }
        }
        client.close();
    }
}