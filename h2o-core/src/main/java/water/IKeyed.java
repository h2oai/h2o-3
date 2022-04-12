package water;

public interface IKeyed<T extends IKeyed> extends Freezable<T> {
  Key<T> getKey();
}
