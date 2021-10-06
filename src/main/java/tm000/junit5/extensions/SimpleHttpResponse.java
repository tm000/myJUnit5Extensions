package tm000.junit5.extensions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(SimpleHttpResponses.class)
public @interface SimpleHttpResponse {
    String value() default "";
    int status() default 200;
    String contentType() default "text/html";
    boolean keepalive() default true;
}
