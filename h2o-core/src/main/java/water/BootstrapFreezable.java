package water;

/**
 * Marker interface - can be used to annotate Freezables that are part of bootstrap class collection.
 * BootstrapFreezables can be used to exchnage H2O-serialized data between different cluster instances
 * without a need to remap type ids because type ids are fixed.
 * 
 * This interface also limits what classes can be deserialized only to known classes with safe behavior.
 * 
 * @param <T>
 */
public interface BootstrapFreezable<T extends BootstrapFreezable<T>> extends Freezable<T> {
}
