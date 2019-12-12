package water.runner;

import org.junit.Ignore;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Ignore
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CloudSize {
    
    int value();
}
