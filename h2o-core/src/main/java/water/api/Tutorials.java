package water.api;

import java.util.Properties;
import water.H2O;
import water.schemas.TutorialsV1;

/**
 * Summary page referencing all tutorials.
 *
 * @author michal
 */
public class Tutorials extends Handler<Tutorials,TutorialsV1> {
  public static final String HTML = 
      "<div class='container'><div class='hero-unit' style='overflow: hidden'>"
    + "<style scoped='scoped'>"
    + "  h2 { font-size:18px; }"
    + "  p { font-size:16px; }"
    + "</style>"
    + "<h1>H<sub>2</sub>O Tutorials</h1>"
    + "<blockquote><small>A unique way to explore H<sub>2</sub>O</small></blockquote>"
    
    + "</div>"
    + "<div class='row'>"
    
    + "<div class='span3 col'>"
    + "  <h2>Random Forest</h2>"
    +   "<p>Random Forest is a classical machine learning algorithm for classification and regression. Learn how to use it with H<sub>2</sub>O.</it></p>"
    +   "<a href='/TutorialRFIris.html' class='btn btn-primary'>Try it!</a>"
    + "</div>"
    
    + "<div class='span3 col'>"
    +   "<h2>GLM</h2>"
    +   "<p>Generalized linear model is a generalization of linear regression. Experience its unique power on top of H<sub>2</sub>O.</p>"
    +   "<a href='/TutorialGLMProstate.html' class='btn btn-primary'>Try it!</a>"
    + "</div>"
    
    + "<div class='span3 col'>"
    + "<h2>K-means</h2>"
    + "<p>Perform cluster analysis with H<sub>2</sub>O. It employs K-means, a highly scalable clustering algorithm.</p>"
    +   "<a href='/TutorialKMeans.html' class='btn btn-primary'>Try it!</a>"
    + "</div>"
    
    + "<div class='span3 col'>"
    + "<h2>Deep Learning</h2>"
    + "<p>H<sub>2</sub>O's distributed Deep Learning models high-level abstractions in data with deep artificial neural networks.</p>"
    +   "<a href='/TutorialDeepLearning.html' class='btn btn-primary'>Try it!</a>"
    + "</div>"
    
    + "</div>"
    + "</div>";

  @Override public void compute2() { throw H2O.fail(); }
  protected void nop() { }
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  // Tutorial Schemas are still at V1, unchanged for V2
  @Override protected TutorialsV1 schema(int version) { return new TutorialsV1(); }
}
