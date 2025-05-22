package common.inject.annotations;

import java.lang.annotation.*;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.CONSTRUCTOR;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(CONSTRUCTOR)
public @interface Inject {
}
