
import java.io.File;

public class H2OApp {
  public static void main( String[] args ) {
    
    // Fire up the H2O Cluster
    water.H2O.main(args);

    // Register a resource lookup directory
    water.H2O.registerResourceRoot(new File(System.getProperty("user.dir") + File.separator +  "h2o-web/src/main/resources/www"));
    water.H2O.registerResourceRoot(new File(System.getProperty("user.dir") + File.separator + "h2o-core/src/main/resources/www"));

    // Register menu items and service handlers for algos
    water.H2O.registerGET("/DeepLearning",hex.schemas.DeepLearningHandler.class,"compute2","/DeepLearning","Deep Learning","Model");
    water.H2O.registerGET("/KMeans",hex.schemas.KMeansHandler.class,"work","/KMeans","KMeans","Model");

    // Done adding menu items; fire up web server
    water.H2O.finalizeRequest();
  }
}
