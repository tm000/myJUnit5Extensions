package tm000.junit5.extensions;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

public class WebServerExtension implements BeforeTestExecutionCallback, BeforeAllCallback, AfterAllCallback {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private int port;
    private boolean enableSecurity;
    private String keyStoreFile;
    private String trustStoreFile;
    private String password;

    private TestHttpServer testServer;

    public List<String> getRequests() {
        return this.testServer.requests;
    }
 
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        this.testServer = new TestHttpServer();
        Thread thread = new Thread(testServer);
        thread.start();
        while (!this.testServer.isReady.get()) {
            Thread.sleep(50);
        }
        logger.info(() -> "Test Server has started!");
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        List<Response> responses = new ArrayList<>();
        Annotation[] annotations = context.getTestMethod().get().getAnnotations();
        Stream.of(annotations).filter(anno -> anno instanceof WebServerResponse | anno instanceof WebServerResponses |
                                    anno instanceof SimpleHttpResponse | anno instanceof SimpleHttpResponses)
            .forEach(anno -> {
                if (anno instanceof WebServerResponses) {
                    WebServerResponses wsrs = (WebServerResponses)anno;
                    Arrays.asList(wsrs.value()).forEach(wsr -> Collections.addAll(responses, new Response(wsr.value(), wsr.keepalive())));
                } else if (anno instanceof WebServerResponse) {
                    WebServerResponse wsr = (WebServerResponse)anno;
                    Collections.addAll(responses, new Response(wsr.value(), wsr.keepalive()));
                } else if (anno instanceof SimpleHttpResponses) {
                    SimpleHttpResponses shrs = (SimpleHttpResponses)anno;
                    Arrays.asList(shrs.value()).forEach(shr -> responses.add(SimpleHttpResponseCreator.create(shr)));
                } else if (anno instanceof SimpleHttpResponse) {
                    Collections.addAll(responses, SimpleHttpResponseCreator.create((SimpleHttpResponse)anno));
                }
            });
        this.testServer.reset();
        this.testServer.responses.addAll(responses);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (this.testServer != null) {
            this.testServer.close();
        }
    }

    /**
     * WebServerExtension Builder
     * @return
     */
    public static WebServerExtensionBuilder builder() {
        return new WebServerExtensionBuilder();
    }

    public static class WebServerExtensionBuilder {
        public int port = 9000;
        public boolean enableSecurity;
        private String keyStoreFile;
        private String trustStoreFile;
        private String password;

        public WebServerExtensionBuilder port(int port) {
            this.port = port;
            return this;
        }
    
        public WebServerExtensionBuilder enableSecurity(boolean es) {
            this.enableSecurity = es;
            return this;
        }

        public WebServerExtensionBuilder keyStoreFile(String keyStoreFile) {
            this.keyStoreFile = keyStoreFile;
            return this;
        }

        public WebServerExtensionBuilder trustStoreFile(String trustStoreFile) {
            this.trustStoreFile = trustStoreFile;
            return this;
        }

        public WebServerExtensionBuilder password(String password) {
            this.password = password;
            return this;
        }

        public WebServerExtension build() {
            if (this.enableSecurity) {
                assertNotNull(this.keyStoreFile);
                assertNotNull(this.trustStoreFile);
                assertNotNull(this.password);
            }
            WebServerExtension wse = new WebServerExtension();
            wse.port = this.port;
            wse.enableSecurity = this.enableSecurity;
            wse.keyStoreFile = this.keyStoreFile;
            wse.trustStoreFile = this.trustStoreFile;
            wse.password = this.password;
            return wse;
        }
    }

    /**
     * Test Http Server
     */
    class TestHttpServer implements Runnable, AutoCloseable {
        final int BUFFER_SIZE = 16384;

        List<String> requests = new ArrayList<>();
        List<Response> responses = new ArrayList<>();
        ServerSocketChannel serverSocket;
        Selector selector;
        boolean isClosing;
        volatile AtomicBoolean isReady = new AtomicBoolean(false);
        int requestCount;
        int appBufferMax;
        int netBufferMax;

        void reset() {
            requests.clear();
            requestCount = 0;
            responses.clear();
        }
    
        @Override
        public void run() {
            try {
                SSLContext context = null;

                if (enableSecurity) {
                    KeyStore ks = KeyStore.getInstance("JKS");
                    KeyStore ts = KeyStore.getInstance("JKS");
                    char[] passphrase = password.toCharArray();
            
                    ks.load(new FileInputStream(keyStoreFile), passphrase);
                    ts.load(new FileInputStream(trustStoreFile), passphrase);
            
                    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                    kmf.init(ks, passphrase);
            
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                    tmf.init(ts);
            
                    context = SSLContext.getInstance("TLS");
                    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
                }

                serverSocket = ServerSocketChannel.open();
                serverSocket.configureBlocking(false);
                serverSocket.socket().bind(new InetSocketAddress(port));
                selector = Selector.open();
                serverSocket.register(selector, SelectionKey.OP_ACCEPT);
                isReady.set(true);
                while (serverSocket.isOpen() && selector.isOpen()) {
                    selector.select(); // blocking operation
                    if (isClosing) break;
                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {
                        SelectionKey selKey = (SelectionKey) it.next();
                        it.remove();

                        if (!selKey.isValid()) {
                        } else if (selKey.isAcceptable()) {
                            // The new client connection is accepted
                            SocketChannel client = serverSocket.accept();
                            client.configureBlocking(false);

                            if (enableSecurity) {
                                SSLEngine engine = context.createSSLEngine();
                                engine.setUseClientMode(false);
                                SSLSession session = engine.getSession();
                                appBufferMax = session.getApplicationBufferSize();
                                netBufferMax = session.getPacketBufferSize();
                                session.invalidate();

                                engine.beginHandshake();
                                if (handshake(client, engine)) {
                                    // The new connection is added to a selector
                                    client.register(selector, SelectionKey.OP_READ, engine);
                                    logger.info(() -> "The new connection is accepted from the client: " + client);
                                } else {
                                    logger.error(() -> "Handshake has failed");
                                    ByteBuffer res = ByteBuffer.wrap("HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n".getBytes());
                                    while (res.hasRemaining()) {
                                        client.write(res);
                                    }
                                    client.close();
                                    selKey.interestOps(SelectionKey.OP_ACCEPT);
                                };
                            } else {
                                appBufferMax = BUFFER_SIZE;
                                netBufferMax = BUFFER_SIZE;
                                client.register(selector, SelectionKey.OP_READ);
                                logger.info(() -> "The new connection is accepted from the client: " + client);
                            }
                        } else if (selKey.isReadable()) {
                            // Data is read from the client
                            SocketChannel client = (SocketChannel)selKey.channel();
                            SSLEngine engine = (SSLEngine)selKey.attachment();
                            int byteReads = read(client, engine);
                            if (byteReads == -1) {
                                client.close();
                            } else if (byteReads > 0) {
                                write(client, engine);
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                logger.error(() -> e.getLocalizedMessage());
                e.printStackTrace();
            }
        }

        private int read(SocketChannel client, SSLEngine engine) throws IOException {
            ByteBuffer peerNetData = ByteBuffer.allocate(netBufferMax);
            int byteReads = 0;
            int totalbyte = 0;
            StringBuilder input = new StringBuilder();
            while ((byteReads = client.read(peerNetData)) > 0) {
                if (engine != null) {
                    peerNetData.flip();
                    ByteBuffer peerAppData = ByteBuffer.allocate(appBufferMax);
                    while (peerNetData.hasRemaining()) {
                        SSLEngineResult result = engine.unwrap(peerNetData, peerAppData);
                        switch (result.getStatus()) {
                        case OK:
                            peerAppData.flip();
                            input.append(new String(peerAppData.array(), 0, peerAppData.array().length).trim());
                            break;
                        case BUFFER_OVERFLOW:
                            peerAppData = ByteBuffer.allocate(peerAppData.capacity() * 2);
                            break;
                        case BUFFER_UNDERFLOW:
                            peerNetData = ByteBuffer.allocate(peerNetData.capacity() * 2);
                            break;
                        case CLOSED:
                            closeConnection(client, engine);
                            return 0;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                        }
                    }
                } else {
                    input.append(new String(peerNetData.array(), 0, byteReads).trim());
                }
                totalbyte += byteReads;
                peerNetData.clear();
            };
            if (input.length() > 0) {
                logger.info(() -> "Client Request:" + input.toString());
                requests.add(input.toString());
            }

            return byteReads == -1 ? -1 : totalbyte;
        }

        private void write(SocketChannel client, SSLEngine engine) throws IOException {
            Response res = responses.get(requestCount < responses.size() ? requestCount++ : responses.size() - 1);
            byte[] message = res.message.getBytes();
            int offset = 0;
            ByteBuffer appData = ByteBuffer.allocate(appBufferMax);
            ByteBuffer netData = ByteBuffer.allocate(netBufferMax);
            while (message.length - offset > 0) {
                appData.put(message, offset, Math.min(message.length - offset, appBufferMax));
                appData.flip();
                while (appData.hasRemaining()) {
                    if (engine != null) {
                        netData.clear();
                        SSLEngineResult result = engine.wrap(appData, netData);
                        switch (result.getStatus()) {
                        case OK:
                            netData.flip();
                            while (netData.hasRemaining()) {
                                client.write(netData);
                            }
                            break;
                        case BUFFER_OVERFLOW:
                            netData = ByteBuffer.allocate(netData.capacity() * 2);
                            break;
                        case BUFFER_UNDERFLOW:
                            appData = ByteBuffer.allocate(appData.capacity() * 2);
                            break;
                        case CLOSED:
                            closeConnection(client, engine);
                            return;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                        }
                    } else {
                        client.write(appData);
                    }
                }
                appData.clear();
                offset += appBufferMax;
            }
            if (!res.keepalive) {
                if (engine != null) {
                    engine.closeOutbound();
                    handshake(client, engine);
                }
                client.close();
            }
        }

        @Override
        public void close() {
            logger.info(() -> "Test Server is closing.");
            try {
                isClosing = true;
                if (serverSocket != null) {
                    serverSocket.close();        
                }
                if (selector != null) {
                    selector.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        boolean handshake(SocketChannel client, SSLEngine engine) throws IOException {
            SSLEngineResult sslResult = null;
            ByteBuffer serverOut = ByteBuffer.wrap("Hello Client, I'm Server".getBytes());
            ByteBuffer clientIn = ByteBuffer.allocateDirect(appBufferMax + 50);
            ByteBuffer cTOs = ByteBuffer.allocateDirect(netBufferMax);
            ByteBuffer sTOc = ByteBuffer.allocateDirect(netBufferMax);
    
            HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
            while (handshakeStatus != HandshakeStatus.FINISHED && handshakeStatus != HandshakeStatus.NOT_HANDSHAKING) {

                switch (handshakeStatus) {
                case NEED_TASK:
                    Runnable runnable;
                    while ((runnable = engine.getDelegatedTask()) != null) {
                        runnable.run();
                    }
                    handshakeStatus = engine.getHandshakeStatus();
                    break;
                case NEED_WRAP:
                    sTOc.clear();
                    sslResult = engine.wrap(serverOut, sTOc);
                    handshakeStatus = sslResult.getHandshakeStatus();
                    switch (sslResult.getStatus()) {
                    case OK:
                        sTOc.flip();
                        while (sTOc.hasRemaining()) {
                            client.write(sTOc);
                        }
                        break;
                    case BUFFER_OVERFLOW:
                        sTOc = ByteBuffer.allocate(sTOc.capacity() * 2);
                        break;
                    case CLOSED:
                        // sTOc.flip();
                        // while (sTOc.hasRemaining()) {
                        //     client.write(sTOc);
                        // }
                        // clientIn.clear();
                        // closeConnection(client, engine);
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + sslResult.getStatus());
                    }
                    break;
                case NEED_UNWRAP:
                    int byteReads = client.read(clientIn);
                    if (byteReads < 0) {
                        engine.closeInbound();
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    clientIn.flip();
                    try {
                        cTOs.clear();
                        sslResult = engine.unwrap(clientIn, cTOs);
                        handshakeStatus = sslResult.getHandshakeStatus();
                    } catch (SSLException e) {
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    clientIn.compact();
                    switch (sslResult.getStatus()) {
                    case OK:
                        break;
                    case BUFFER_OVERFLOW:
                        cTOs = ByteBuffer.allocate(sTOc.capacity() * 2);
                        break;
                    case BUFFER_UNDERFLOW:
                        // clientIn buffer is already max size.
                        break;
                    case CLOSED:
                        closeConnection(client, engine);
                        handshakeStatus = engine.getHandshakeStatus();
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + sslResult.getStatus());
                    }
                    break;
                default:
                    break;
                }
            }
            return handshakeStatus == HandshakeStatus.FINISHED;
        }

        void closeConnection(SocketChannel client, SSLEngine engine) throws IOException {
            engine.closeOutbound();
            handshake(client, engine);
            client.close();
        }
    }

    static class Response {
        String message;
        boolean keepalive;
        Response(String value, boolean keepalive) {
            this.message = value;
            this.keepalive = keepalive;
        }
        @Override
        public String toString() {
            return "Response[message=" + this.message +
                    ", keepalive=" + this.keepalive +
                    "]";
        }
    }
}
