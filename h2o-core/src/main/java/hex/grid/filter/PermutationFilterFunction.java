package hex.grid.filter;

import hex.Model;

import java.util.function.Predicate;

public interface PermutationFilterFunction<MP extends Model.Parameters>
        extends FilterFunction<MP>, Predicate<MP>, Activatable {
}
