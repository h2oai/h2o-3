package water;

public interface BootstrapFreezable<T extends BootstrapFreezable<T>> extends Freezable<T> {
}
