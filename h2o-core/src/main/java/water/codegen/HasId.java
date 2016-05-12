package water.codegen;

public interface HasId<T> {

  T withId(String id);

  String id();
}
