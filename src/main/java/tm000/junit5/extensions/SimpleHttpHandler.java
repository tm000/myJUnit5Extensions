package tm000.junit5.extensions;

public class SimpleHttpHandler {
    public String handle(String input) {
        SimpleHttpRequest request = new SimpleHttpRequestParser().parse(input);
        return (request == null ? null : request.toString());
    }
}
