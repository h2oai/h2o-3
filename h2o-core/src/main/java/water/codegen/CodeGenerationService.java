package water.codegen;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.ServiceLoader;

import hex.Model;
import water.codegen.driver.CodeGenOutputDriver;
import water.codegen.driver.DirectOutputDriver;
import water.codegen.java.JavaCodeGenerator;
import water.codegen.java.POJOModelCodeGenerator;
import water.parser.ParserProvider;
import water.util.Log;

/**
 * Model code generation service provider.
 *
 * This is main entry point for model generator.
 *
 * Note: only Java generators are now supported
 */
public class CodeGenerationService {

  public static CodeGenerationService INSTANCE = new CodeGenerationService();

  /** Service loader.
   *
   * Based on JavaDoc of SPI: "Instances of this class are not safe for use by multiple concurrent threads." - all usages of the loader
   * are protected by synchronized block.
   */
  private final ServiceLoader<JavaCodeGenerator.GeneratorProvider> loader;

  private CodeGenerationService() {
    loader = ServiceLoader.load(JavaCodeGenerator.GeneratorProvider.class);
  }

  /** Generate source code for given model.
   *
   * Note: this method is only suitable for small models. Large models
   * should be generated in streaming way.
   *
   * @param model  a model
   * @return  a string representing model code
   */
  public String generate(Model model) {
    ByteArrayOutputStream fos = new ByteArrayOutputStream(1 << 16);

    fos = generate(model, new DirectOutputDriver(), fos);
    return new String(fos.toByteArray());
  }

  /**
   * Generate model code into given output stream with help of given output driver.
   *
   * @param model  model to generate code for
   * @param outputDriver  a class driving code generation
   * @param os  output stream where generated code is written to
   * @return  passed output stream
   */
  public <O extends OutputStream> O generate(Model model, CodeGenOutputDriver outputDriver, O os) {
    POJOModelCodeGenerator codegen = getGenerator(model);
    if (codegen == null) {
      throw new IllegalArgumentException("Cannot find code generator for model: " + model);
    }

    try {
      codegen.build();
      outputDriver.codegen(codegen, os);
      return os;
    } catch (IOException e) {
      Log.debug("Cannot generate source code!", e);
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns code generator for given model.
   * @param model  model to generate code for
   * @param <M>  model type
   * @param <G>  pojo model code generator type
   * @return pojo model code generator or null if it is not found
   */
  public <M extends Model<M, ?, ?>, G extends POJOModelCodeGenerator<G, M>> G getGenerator(M model) {
    Class modelKlazz = model.getClass();
    for (JavaCodeGenerator.GeneratorProvider gprovider : loader) {
      if (gprovider.supports(modelKlazz)) {
        return (G) gprovider.createGenerator(model);
      }
    }
    return null;
  }

  /**
   * Return true if a code generator for model exists.
   *
   * Note: right now only Java code generator can exist
   * @param model  a model
   * @return  true if a generator for given model exists.
   */
  public boolean hasCodeGenerator(Model model) {
    return getGenerator(model) != null;
  }
}
