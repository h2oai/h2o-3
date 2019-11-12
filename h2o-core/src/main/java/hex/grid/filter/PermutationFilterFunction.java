package hex.grid.filter;


import java.util.Map;

/**
 * By using {@link FunctionalInterface} it is possible to define lambda functions of a corresponding type. 
 * Note: interface with {@link FunctionalInterface} will not provide full functional support ( e.g. no `compose` and `andThen` methods)
 */
@FunctionalInterface
public interface PermutationFilterFunction {
  Boolean apply(Map<String, Object> permutation);
}
