package hex.grid.filter;


import hex.Model;

/**
 * Assumed to represent higher level of abstraction comparing to {@link java.util.function.Predicate}
 * 
 * By using {@link FunctionalInterface} it is possible to define lambda functions of a corresponding type. 
 * Note: interface with {@link FunctionalInterface} will not provide full functional support ( e.g. no `compose` and `andThen` methods)
 */
@FunctionalInterface
public interface FilterFunction<MP extends Model.Parameters> {
  Boolean apply(MP permutation);
}
