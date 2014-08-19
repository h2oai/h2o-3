package water;

import hex.ModelBuilder;
import hex.example.Example;
import hex.kmeans.KMeans;
import water.H2O;

import java.io.File;

public class H2OApp {
  public static void main2( String relpath ) { driver(new String[0],relpath); }

  public static void main( String[] args  ) { driver(args,System.getProperty("user.dir")); }

  private static void driver( String[] args, String relpath ) {

    // Fire up the H2O Cluster
    H2O.main(args);

    H2O.registerResourceRoot(new File(relpath + File.separator + "h2o-web/src/main/resources/www"));
    H2O.registerResourceRoot(new File(relpath + File.separator + "h2o-core/src/main/resources/www"));

    // Register menu items and service handlers for algos
    // H2O.registerGET("/DeepLearning",hex.schemas.DeepLearningHandler.class,"compute2","/DeepLearning","Deep Learning","Model");
    // TODO: put back: water.H2O.registerGET("/DeepLearning",hex.schemas.DeepLearningHandler.class,"compute2","/DeepLearning","Deep Learning","Model");
    H2O.registerGET("/KMeans",hex.schemas.KMeansHandler.class,"train","/KMeans","KMeans","Model");
    H2O.registerGET("/Example",hex.schemas.ExampleHandler.class,"work","/Example","Example","Model");

    // An empty Job for testing job polling
    // TODO: put back:
    // H2O.registerGET("/SlowJob", SlowJobHandler.class, "work", "/SlowJob", "Slow Job", "Model");

    ModelBuilder.registerModelBuilder("kmeans", KMeans.class);
    ModelBuilder.registerModelBuilder("example", Example.class);

    // Done adding menu items; fire up web server
    H2O.finalizeRequest();
  }
}
