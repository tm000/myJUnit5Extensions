package tm000.junit5.extensions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleHttpRequestParser {
    public SimpleHttpRequest parse(String input) {
        SimpleHttpRequest req = new SimpleHttpRequest();
        Pattern pattern = Pattern.compile("(OPTIONS|GET|HEAD|POST|PUT|DELETE|TRACE|CONNECT)\\s([^\\s]+)\\sHTTP/1.1\\b");
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            req.method(matcher.group(1));
            req.url(matcher.group(2));
            req.protocol("HTTP/1.1");
            return req;
        } else {
            return null;
        }
    }
}
