package modder.hub.editor.component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/* Replicated from androidx annotation */

@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.PACKAGE})
@Documented
@Retention(RetentionPolicy.CLASS)
public @interface RequiresApi {
    int api() default 1;

    int value() default 1;
}

