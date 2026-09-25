package jks.tools2d.parallax;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a member the browser build does not have: the GWT compiler drops anything annotated with a type of this simple
 * name. What it names must be written fully qualified, never imported, when it lives in {@code core/src-jvm}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD })
public @interface GwtIncompatible
{
	/** Why the member is not in the browser build. */
	String value() default "";
}
