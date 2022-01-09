package tm000.junit5.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;

public class WebServerExtensionEnableSecuriyTest {
    static final String KEYSTORE_PATH = "/home/ubuntu/git/myJUnit5Extensions/keystore.jks";
    static final String PASSWORD = "passphrase";

    @RegisterExtension
    static WebServerExtension server = WebServerExtension.builder()
        .enableSecurity(true)
        .keyStoreFile(KEYSTORE_PATH)
        .trustStoreFile(KEYSTORE_PATH)
        .password(PASSWORD)
        .build();

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        System.out.println(testInfo.getDisplayName() + " start --------------------------------------------------------------------------------------");
    }

    @Test
    @SimpleHttpResponse(value = "<html><head><title>Test Http Server</title></head><body><h1>Hello world!</h1></body></html>", keepalive = false)
    @SimpleHttpResponse(status = 400, contentType = "text/plain", keepalive = false)
    @SimpleHttpResponse(status = 500, contentType = "text/plain", keepalive = false)
    void requestUsingNioSocketChannel() throws Exception {
        InetSocketAddress hA = new InetSocketAddress("localhost", 9000);
        // Create/initialize the SSLContext with key material
        char[] passphrase = PASSWORD.toCharArray();

        // First initialize the key and trust material.
        KeyStore ksKeys = KeyStore.getInstance("JKS");
        ksKeys.load(new FileInputStream(KEYSTORE_PATH), passphrase);
        KeyStore ksTrust = KeyStore.getInstance("JKS");
        ksTrust.load(new FileInputStream(KEYSTORE_PATH), passphrase);

        // KeyManager's decide which key material to use.
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ksKeys, passphrase);

        // TrustManager's decide whether to allow connections.
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ksTrust);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
            kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        // The Client is sending messages to server...
        String[] msg = new String [] {"Hello.", "World", "Bye Bye"};
        for (int i = 0; i < msg.length; i++) {
            SocketChannel client = SocketChannel.open(hA);
            client.configureBlocking(false);
            while (!client.finishConnect()) {
                // System.out.println("still connecting");
            }

            // We're ready for the engine.
            SSLEngine engine = sslContext.createSSLEngine("localhost", 9000);

            // Use as client
            engine.setUseClientMode(true);
            // Create byte buffers to use for holding application and encoded data
            SSLSession session = engine.getSession();
            ByteBuffer myNetData = ByteBuffer.allocate(session.getPacketBufferSize());
            ByteBuffer peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());
            // Do initial handshake
            engine.beginHandshake();
            if (doHandshake(client, engine, myNetData, peerNetData)) {
                Thread.sleep(50);
                write(client, engine, msg[i], myNetData, peerNetData);
                String response = null;
                while (true) {
                    response = read(client, engine, peerNetData);
                    if (response.length() > 0) {
                        break;
                    } else {
                        Thread.sleep(30);
                    }
                }
                // check response
                switch (i) {
                    case 0:
                        assertEquals("HTTP/1.1 200 OK\r\nContent-Length: 91\r\nConnection: close\r\nContent-Type: text/html\r\n\r\n<html><head><title>Test Http Server</title></head><body><h1>Hello world!</h1></body></html>", response);
                        break;
                    case 1:
                        assertEquals("HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\nConnection: close\r\nContent-Type: text/plain", response);
                        break;
                    default:
                        assertEquals("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\nContent-Type: text/plain", response);
                }
            } else {
                System.out.println("Handshake has failed.");
            }
            myNetData.clear();
            peerNetData.clear();

            engine.closeOutbound();
            doHandshake(client, engine, myNetData, peerNetData);
            client.close();
        }
        assertEquals("Hello.", server.getRequests().get(0));
        assertEquals("World", server.getRequests().get(1));
        assertEquals("Bye Bye", server.getRequests().get(2));
    }

    private boolean doHandshake(SocketChannel client, SSLEngine engine, ByteBuffer cTOs, ByteBuffer sTOc) throws IOException {
		HandshakeStatus hsStatus = engine.getHandshakeStatus();
        ByteBuffer clientOut = ByteBuffer.allocate(cTOs.capacity());
        ByteBuffer clientIn = ByteBuffer.allocate(sTOc.capacity());
        while (hsStatus != HandshakeStatus.FINISHED && hsStatus != HandshakeStatus.NOT_HANDSHAKING) {
            SSLEngineResult result = null;
            switch (hsStatus) {
                case NEED_TASK:
                    Runnable runnable;
                    while ((runnable = engine.getDelegatedTask()) != null) {
                        runnable.run();
                    }
                    hsStatus = engine.getHandshakeStatus();
                    break;
                case NEED_WRAP:
                    cTOs.clear();
                    result = engine.wrap(clientOut, cTOs);
                    hsStatus = result.getHandshakeStatus();
                    switch (result.getStatus()) {
                    case OK:
                        cTOs.flip();
                        while (cTOs.hasRemaining()) {
                            client.write(cTOs);
                        }
                        break;
                    case BUFFER_OVERFLOW:
                        cTOs = ByteBuffer.allocate(cTOs.capacity() * 2);
                        break;
                    case BUFFER_UNDERFLOW:
                        break;
                    case CLOSED:
                        cTOs.flip();
                        while (cTOs.hasRemaining()) {
                            client.write(cTOs);
                        }
                        clientIn.clear();
                        break;
                    default:
                        throw new IllegalStateException("[C]Invalid SSL status: " + result.getStatus());
                    }
                    break;
                case NEED_UNWRAP:
                    int byteReads = client.read(clientIn);
                    if (byteReads < 0) {
                        engine.closeInbound();
                        engine.closeOutbound();
                        hsStatus = engine.getHandshakeStatus();
                        break;
                    }
                    clientIn.flip();
                    try {
                        cTOs.clear();
                        result = engine.unwrap(clientIn, cTOs);
                        clientIn.compact();
                        hsStatus = result.getHandshakeStatus();
                    } catch (SSLException e) {
                        engine.closeOutbound();
                        hsStatus = engine.getHandshakeStatus();
                        break;
                    }
                    switch (result.getStatus()) {
                    case OK:
                        break;
                    case BUFFER_OVERFLOW:
                        cTOs = ByteBuffer.allocate(cTOs.capacity() * 2);
                        break;
                    case BUFFER_UNDERFLOW:
                        clientIn.clear();
                        // clientIn = ByteBuffer.allocate(clientIn.capacity() * 2);
                        break;
                    case CLOSED:
                        engine.closeOutbound();
                        hsStatus = engine.getHandshakeStatus();
                        break;
                    default:
                        throw new IllegalStateException("[C]Invalid SSL status: " + result.getStatus());
                    }
                    break;
                default:
                    break;
            }
        }
        return hsStatus == HandshakeStatus.FINISHED;
    }

    private boolean write(SocketChannel client, SSLEngine engine, String message, ByteBuffer myNetData, ByteBuffer peerNetData) throws IOException {
        myNetData.clear();
        peerNetData.clear();
        SSLEngineResult rslt = null;
        rslt = engine.wrap(ByteBuffer.wrap(message.getBytes()), myNetData);
        switch (rslt.getStatus()) {
        case OK:
            myNetData.flip();
            while (myNetData.hasRemaining()) {
                client.write(myNetData);
           }
            return true;
        case BUFFER_OVERFLOW:
            myNetData = ByteBuffer.allocate(myNetData.capacity() * 2);
            break;
        case BUFFER_UNDERFLOW:
            break;
        case CLOSED:
            engine.closeOutbound();
            doHandshake(client, engine, myNetData, peerNetData);
            client.close();
            break;
        default:
            throw new IllegalStateException("Invalid SSL status: " + rslt.getStatus());
        }

        return false;
    }

    private String read(SocketChannel client, SSLEngine engine, ByteBuffer peerNetData) throws SSLException, IOException {
        ByteBuffer peerAppData = ByteBuffer.allocate(peerNetData.capacity());
        peerNetData.clear();

        StringBuilder response = new StringBuilder();
        int byteReads = 0;
        while ((byteReads = client.read(peerNetData)) > 0) {
            peerNetData.flip();
            while (peerNetData.hasRemaining()) {
                peerAppData.clear();
                SSLEngineResult rslt = engine.unwrap(peerNetData, peerAppData);
                switch (rslt.getStatus()) {
                case OK:
                    response.append(new String(peerAppData.array(), 0, peerAppData.position()).trim());
                    break;
                case BUFFER_OVERFLOW:
                    peerAppData = ByteBuffer.allocate(peerAppData.capacity() * 2);
                    break;
                case BUFFER_UNDERFLOW:
                    peerNetData = ByteBuffer.allocate(peerNetData.capacity() * 2);
                    break;
                case CLOSED:
                    engine.closeOutbound();
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + rslt.getStatus());
                }
            }
        }

        return response.toString();
    }

    @Test
    @SimpleHttpResponse(value="<html><head><title>Test Http Server</title></head><body><h1>Hello world!</h1></body></html>", keepalive = true)
    @SimpleHttpResponse(status=400, contentType = "text/plain")
    @SimpleHttpResponse(status=500, contentType = "text/plain", keepalive = false)
    void requestUsingApacheHttpClient() throws Exception {
        CloseableHttpClient httpclient = null;
        try {
            //Creating SSLContextBuilder object
            SSLContextBuilder SSLBuilder = SSLContexts.custom();
            //Loading the Keystore file
            File file = new File(KEYSTORE_PATH);
            SSLBuilder = SSLBuilder.loadTrustMaterial(file, PASSWORD.toCharArray());
            //Building the SSLContext usiong the build() method
            SSLContext sslcontext = SSLBuilder.build();
            //Creating SSLConnectionSocketFactory object
            SSLConnectionSocketFactory sslConSocFactory = new SSLConnectionSocketFactory(sslcontext, new NoopHostnameVerifier());
            //Creating HttpClientBuilder
            HttpClientBuilder clientbuilder = HttpClients.custom();
            //Setting the SSLConnectionSocketFactory
            clientbuilder = clientbuilder.setSSLSocketFactory(sslConSocFactory);
            //Building the CloseableHttpClient
            httpclient = clientbuilder.build();
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

            //Creating the HttpGet request
            HttpGet httpget = new HttpGet("https://localhost:9000");
            String responseBody = httpclient.execute(httpget, responseHandler);
            assertEquals("<html><head><title>Test Http Server</title></head><body><h1>Hello world!</h1></body></html>", responseBody);

            httpget = new HttpGet("https://localhost:9000/static/img");
            responseBody = httpclient.execute(httpget, responseHandler);
            assertEquals("statusCode=400", responseBody);

            httpget = new HttpGet("https://localhost:9000/files");
            responseBody = httpclient.execute(httpget, responseHandler);
            assertEquals("statusCode=500", responseBody);
        } finally {
            httpclient.close();
        }
    }
}