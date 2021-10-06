package tm000.junit5.extensions;

public class SimpleHttpRequest {
    private String method;
    private String url;
    private String protocol;

    public SimpleHttpRequest method(String method) {
        this.method = method;
        return this;
    }

    public SimpleHttpRequest url(String url) {
        this.url = url;
        return this;
    }

    public SimpleHttpRequest protocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    @Override
    public String toString() {
        return "SimpleHttpRequest[" +
                "method=" + this.method +
                ", url=" + this.url +
                ", protocol=" + this.protocol +
                "]";
    }
}
