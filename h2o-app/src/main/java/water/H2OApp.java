package water;

import hex.ModelBuilder;
import hex.api.DeepLearningBuilderHandler;
import hex.api.ExampleBuilderHandler;
import hex.api.GLMBuilderHandler;
import hex.api.KMeansBuilderHandler;
import hex.deeplearning.DeepLearning;
import hex.example.Example;
import hex.glm.GLM;
import hex.kmeans.KMeans;

import java.io.File;

public class H2OApp {
  public static void main2( String relpath ) { driver(new String[0],relpath); }

  public static void main( String[] args  ) { driver(args,System.getProperty("user.dir")); }

  private static void driver( String[] args, String relpath ) {

    // Fire up the H2O Cluster
    H2O.main(args);

    H2O.registerResourceRoot(new File(relpath + File.separator + "h2o-web/training_frame/main/resources/www"));
    H2O.registerResourceRoot(new File(relpath + File.separator + "h2o-core/training_frame/main/resources/www"));

    // Register menu items and service handlers for algos
    H2O.registerGET("/DeepLearning",hex.schemas.DeepLearningHandler.class,"train","/DeepLearning","Deep Learning","Model");
    H2O.registerGET("/KMeans",hex.schemas.KMeansHandler.class,"train","/KMeans","KMeans","Model");
    H2O.registerGET("/Example",hex.schemas.ExampleHandler.class,"train","/Example","Example","Model");

    // An empty Job for testing job polling
    // TODO: put back:
    // H2O.registerGET("/SlowJob", SlowJobHandler.class, "work", "/SlowJob", "Slow Job", "Model");

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Register the algorithms and their builder handlers:
    ModelBuilder.registerModelBuilder("kmeans", KMeans.class);
    H2O.registerPOST("/2/ModelBuilders/kmeans", KMeansBuilderHandler.class, "train");

    ModelBuilder.registerModelBuilder("deeplearning", DeepLearning.class);
    H2O.registerPOST("/2/ModelBuilders/deeplearning", DeepLearningBuilderHandler.class, "train");

    ModelBuilder.registerModelBuilder("glm", GLM.class);
    H2O.registerPOST("/2/ModelBuilders/glm", GLMBuilderHandler.class, "train");

    ModelBuilder.registerModelBuilder("example", Example.class);
    H2O.registerPOST("/2/ModelBuilders/example", ExampleBuilderHandler.class, "train");

    // Done adding menu items; fire up web server
    H2O.finalizeRequest();
  }
}
