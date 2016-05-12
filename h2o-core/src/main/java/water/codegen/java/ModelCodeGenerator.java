package water.codegen.java;

import hex.Model;
import water.codegen.CodeGeneratorPipeline;

/**
 * Created by michal on 3/28/16.
 */
public abstract class ModelCodeGenerator<S extends ModelCodeGenerator<S, M>, M extends Model<M, ?, ?>>
    extends CodeGeneratorPipeline<S, CompilationUnitGenerator>
    implements ClassGenContainer {

}
