package water.util.annotations;

/**
 * The file was copied from animal-sniffer-annotations project:
 * https://github.com/mojohaus/animal-sniffer/ to avoid another dependency
 * to the h2o-core project.
 *
 * The animal-sniffer-annotations is release under MIT licence.
 *
 */
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.RetentionPolicy.CLASS;

@Retention(CLASS)
@Documented
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
public @interface IgnoreJRERequirement
{
}
