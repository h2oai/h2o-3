package water.api;

import java.util.Properties;
import water.H2O;

/**
 * Summary page referencing all tutorials.
 *
 * @author michal
 */
public class Tutorials extends Handler {
  Tutorials() { super(1,Integer.MAX_VALUE); }
  @Override protected Response serve(RequestServer server, String uri, String method, Properties parms, RequestType type) {
    return new Response("<div class='container'><div class='hero-unit' style='overflow: hidden'>"
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
    + "</div>");
  }
}
