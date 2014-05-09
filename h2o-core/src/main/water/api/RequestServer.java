package water.api;

//import hex.GridSearch.GridSearchProgress;
//import hex.KMeans2;
//import hex.KMeans2.KMeans2ModelView;
//import hex.KMeans2.KMeans2Progress;
//import hex.ReBalance;
//import hex.deeplearning.DeepLearning;
//import hex.drf.DRF;
//import hex.gapstat.GapStatistic;
//import hex.gapstat.GapStatisticModelView;
//import hex.gbm.GBM;
//import hex.glm.GLM2;
//import hex.glm.GLMGridView;
//import hex.glm.GLMModelView;
//import hex.glm.GLMProgress;
//import hex.nb.NBModelView;
//import hex.nb.NBProgressPage;
//import hex.gapstat.GapStatisticProgressPage;
//import hex.nb.NaiveBayes;
//import hex.pca.PCA;
//import hex.pca.PCAModelView;
//import hex.pca.PCAProgressPage;
//import hex.pca.PCAScore;
//import hex.singlenoderf.SpeeDRF;
//import hex.singlenoderf.SpeeDRFModelView;
//import hex.singlenoderf.SpeeDRFProgressPage;
//import water.Boot;
//import water.H2O;
import water.NanoHTTPD;
import water.util.Log;

import java.io.*;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** This is a simple web server. */
public class RequestServer extends NanoHTTPD {
  protected enum API_VERSION {
    V_1(1, "/"),
    V_2(2, "/2/"); // FIXME: better should be /v2/
    final private int _version;
    final String _prefix;
    private API_VERSION(int version, String prefix) { _version = version; _prefix = prefix; }
  }
  static RequestServer SERVER;
  private RequestServer( ServerSocket socket ) throws IOException { super(socket,null); }

  // cache of all loaded resources
  private static final ConcurrentHashMap<String,byte[]> _cache = new ConcurrentHashMap();
  protected static final HashMap<String,Request> _requests = new HashMap();

  static final Request _http404;
  static final Request _http500;

  // initialization ------------------------------------------------------------
  static {
    _http404 = registerRequest(new HTTP404());
    _http500 = registerRequest(new HTTP500());

    // Data
    Request.addToNavbar(registerRequest(new ImportFiles()),  "Import Files",           "Data");
    //Request.addToNavbar(registerRequest(new Upload2()),       "Upload",                 "Data");
    //Request.addToNavbar(registerRequest(new Parse2()),        "Parse",                  "Data");
    //Request.addToNavbar(registerRequest(new Inspector()),     "Inspect",                "Data");
    //Request.addToNavbar(registerRequest(new SummaryPage2()),  "Summary",                "Data");
    //Request.addToNavbar(registerRequest(new QuantilesPage()), "Quantiles",              "Data");
    //Request.addToNavbar(registerRequest(new StoreView()),     "View All",               "Data");
    //Request.addToNavbar(registerRequest(new ExportFiles()),   "Export Files",           "Data");
    //// Register Inspect2 just for viewing frames
    //registerRequest(new Inspect2());
    //
    //// FVec models
    //Request.addToNavbar(registerRequest(new PCA()),         "PCA",                      "Model");
    //Request.addToNavbar(registerRequest(new GBM()),         "GBM",                      "Model");
    //Request.addToNavbar(registerRequest(new DeepLearning()),"Deep Learning",            "Model");
    //Request.addToNavbar(registerRequest(new DRF()),         "Distributed RF (Beta)",    "Model");
    //Request.addToNavbar(registerRequest(new GLM2()),        "GLM (Beta)",               "Model");
    //Request.addToNavbar(registerRequest(new KMeans2()),     "KMeans (Beta)",            "Model");
    //Request.addToNavbar(registerRequest(new NaiveBayes()),  "Naive Bayes (Beta)",       "Model");
    //
    //// FVec scoring
    //Request.addToNavbar(registerRequest(new Predict()),     "Predict",                  "Score");
    //Request.addToNavbar(registerRequest(new ConfusionMatrix()), "Confusion Matrix",     "Score");
    //Request.addToNavbar(registerRequest(new AUC()),         "AUC",                      "Score");
    //Request.addToNavbar(registerRequest(new HitRatio()),    "HitRatio",                 "Score");
    //Request.addToNavbar(registerRequest(new PCAScore()),    "PCAScore",                 "Score");
    //Request.addToNavbar(registerRequest(new Steam()),    "Multi-model Scoring (Beta)", "Score");
    //
    //// Admin
    //Request.addToNavbar(registerRequest(new Jobs()),        "Jobs",                     "Admin");
    //Request.addToNavbar(registerRequest(new Cloud()),       "Cluster Status",           "Admin");
    //Request.addToNavbar(registerRequest(new IOStatus()),    "Cluster I/O",              "Admin");
    //Request.addToNavbar(registerRequest(new Timeline()),    "Timeline",                 "Admin");
    //Request.addToNavbar(registerRequest(new JStack()),      "Stack Dump",               "Admin");
    //Request.addToNavbar(registerRequest(new JProfile()),    "Profile Dump",             "Admin");
    //Request.addToNavbar(registerRequest(new Debug()),       "Debug Dump",               "Admin");
    //Request.addToNavbar(registerRequest(new LogView()),     "Inspect Log",              "Admin");
    //Request.addToNavbar(registerRequest(new Shutdown()),    "Shutdown",                 "Admin");
    //
    //// Help and Tutorials
    //Request.addToNavbar(registerRequest(new Documentation()),       "H2O Documentation",      "Help");
    Request.addToNavbar(registerRequest(new Tutorials()),           "Tutorials Home",         "Help");
    //Request.addToNavbar(registerRequest(new TutorialGBM()),         "GBM Tutorial",           "Help");
    //Request.addToNavbar(registerRequest(new TutorialDeepLearning()),"Deep Learning Tutorial", "Help");
    //Request.addToNavbar(registerRequest(new TutorialRFIris()),      "Random Forest Tutorial", "Help");
    //Request.addToNavbar(registerRequest(new TutorialGLMProstate()), "GLM Tutorial",           "Help");
    //Request.addToNavbar(registerRequest(new TutorialKMeans()),      "KMeans Tutorial",        "Help");
    //Request.addToNavbar(registerRequest(new AboutH2O()),            "About H2O",              "Help");

    //Request.addToNavbar(registerRequest(new hex.LR2()),        "Linear Regression2",   "Beta");
    //Request.addToNavbar(registerRequest(new ReBalance()),      "ReBalance",            "Beta");
    //Request.addToNavbar(registerRequest(new FrameSplitPage()), "Split frame",          "Beta");
    //Request.addToNavbar(registerRequest(new Console()),        "R-Like Console",       "Beta");
    //Request.addToNavbar(registerRequest(new GapStatistic()),   "Gap Statistic",        "Beta");
    //Request.addToNavbar(registerRequest(new SpeeDRF()),        "SpeeDRF",              "Beta");
    //Request.addToNavbar(registerRequest(new UnlockKeys()),     "Unlock Keys",          "Beta");


    // internal handlers
    //registerRequest(new Get()); // Download
    //registerRequest(new OneHot());
    //registerRequest(new Cancel());
    //registerRequest(new DRFModelView());
    //registerRequest(new DRFProgressPage());
    //registerRequest(new DownloadDataset());
    //registerRequest(new Exec2());
    //registerRequest(new ExportS3Progress());
    //registerRequest(new GBMModelView());
    //registerRequest(new GBMProgressPage());
    //registerRequest(new GLMGridProgress());
    //registerRequest(new GLMProgressPage());
    //registerRequest(new GridSearchProgress());
    //registerRequest(new LogView.LogDownload());
    //registerRequest(new NeuralNetModelView());
    //registerRequest(new NeuralNetProgressPage());
    //registerRequest(new DeepLearningModelView());
    //registerRequest(new DeepLearningProgressPage());
    //registerRequest(new KMeans2Progress());
    //registerRequest(new KMeans2ModelView());
    //registerRequest(new NBProgressPage());
    //registerRequest(new GapStatisticProgressPage());
    //registerRequest(new NBModelView());
    //registerRequest(new GapStatisticModelView());
    //registerRequest(new PCAProgressPage());
    //registerRequest(new PCAModelView());
    //registerRequest(new PostFile());
    //registerRequest(new water.api.Upload2.PostFile());
    //registerRequest(new Progress());
    //registerRequest(new Progress2());
    //registerRequest(new PutValue());
    //registerRequest(new RFTreeView());
    //registerRequest(new RFView());
    //registerRequest(new RReaderProgress());
    //registerRequest(new Remove());
    //registerRequest(new RemoveAll());
    //registerRequest(new RemoveAck());
    //registerRequest(new SetColumnNames());
    //registerRequest(new SpeeDRFModelView());
    //registerRequest(new SpeeDRFProgressPage());
    //registerRequest(new water.api.SetColumnNames2());     // Set colnames for FluidVec objects
    //registerRequest(new LogAndEcho());
    //registerRequest(new ToEnum());
    //registerRequest(new ToEnum2());
    //registerRequest(new ToInt2());
    //registerRequest(new GLMProgress());
    //registerRequest(new hex.glm.GLMGridProgress());
    //registerRequest(new water.api.Levels2());    // Temporary hack to get factor levels efficiently
    //registerRequest(new water.api.Levels());    // Ditto the above for ValueArray objects
    //// Typeahead
    //registerRequest(new TypeaheadModelKeyRequest());
    //registerRequest(new TypeaheadGLMModelKeyRequest());
    //registerRequest(new TypeaheadRFModelKeyRequest());
    //registerRequest(new TypeaheadKMeansModelKeyRequest());
    //registerRequest(new TypeaheadPCAModelKeyRequest());
    //registerRequest(new TypeaheadHexKeyRequest());
    //registerRequest(new TypeaheadFileRequest());
    //registerRequest(new TypeaheadHdfsPathRequest());
    //registerRequest(new TypeaheadKeysRequest("Existing H2O Key", "", null));
    //registerRequest(new TypeaheadS3BucketRequest());
    //// testing hooks
    //registerRequest(new TestPoll());
    //registerRequest(new TestRedirect());
    //registerRequest(new GLMModelView());
    //registerRequest(new GLMGridView());
    //registerRequest(new LaunchJar());
    //
    //// Pure APIs, no HTML, to support The New World
    //registerRequest(new Models());
    //registerRequest(new Frames());
    //registerRequest(new ModelMetrics());

    Request.initializeNavBar();
  }

  /** Registers the request with the request server.  */
  private static Request registerRequest(Request req) {
    assert req.supportedVersions().length > 0;
    for( API_VERSION ver : req.supportedVersions() ) {
      String href = req.href(ver);
      assert !_requests.containsKey(href) : "Request with href "+href+" already registered";
      _requests.put(href,req);
    }
    return req;
  }

  // Keep spinning until we get to launch the NanoHTTPD.  Launched in a
  // seperate thread (I'm guessing here) so the startup process does not hang
  // if the various web-port accesses causes Nano to hang on startup.
  public static void start() {
    new Thread( new Runnable() {
        @Override public void run()  {
          while( true ) {
            try {
              // Try to get the NanoHTTP daemon started
              SERVER = new RequestServer(water.init.NetworkInit._apiSocket);
              break;
            } catch( Exception ioe ) {
              Log.err("Launching NanoHTTP server got ",ioe);
              try { Thread.sleep(1000); } catch( InterruptedException ignore ) { } // prevent denial-of-service
            }
          }
        }
      }, "Request Server launcher").start();
  }

  private static String maybeTransformRequest (String uri) {
    if (uri.isEmpty() || uri.equals("/"))
      return "/Tutorials.html";

    Pattern p = Pattern.compile("/R/bin/([^/]+)/contrib/([^/]+)(.*)");
    Matcher m = p.matcher(uri);
    boolean b = m.matches();
    if (b) {
      // On Jenkins, this command sticks his own R version's number
      // into the package that gets built.
      //
      //     R CMD INSTALL -l $(TMP_BUILD_DIR) --build h2o-package
      //
      String versionOfRThatJenkinsUsed = "3.0";

      String platform = m.group(1);
      //String version = m.group(2);
      String therest = m.group(3);
      return "/R/bin/" + platform + "/contrib/" + versionOfRThatJenkinsUsed + therest;
    }

    return uri;
  }

  // Log all requests except the overly common ones
  void maybeLogRequest (String uri, String method, Properties parms) {
    if (uri.endsWith(".css")) return;
    if (uri.endsWith(".js")) return;
    if (uri.endsWith(".png")) return;
    if (uri.endsWith(".ico")) return;
    if (uri.startsWith("/Typeahead")) return;
    if (uri.startsWith("/2/Typeahead")) return;
    if (uri.startsWith("/Cloud.json")) return;
    if (uri.endsWith("LogAndEcho.json")) return;
    if (uri.contains("Progress")) return;
    if (uri.startsWith("/Jobs.json")) return;

    String log = String.format("%-4s %s", method, uri);
    for( Object arg : parms.keySet() ) {
      String value = parms.getProperty((String) arg);
      if( value != null && value.length() != 0 )
        log += " " + arg + "=" + value;
    }
    Log.info(log);
  }

  // Top-level dispatch based the URI.  Break down URI into parts;
  // e.g. /2/Tutorials.html breaks down into requestName "/2/Tutorials" and
  // requestType ".html".
  @Override public NanoHTTPD.Response serve( String uri, String method, Properties header, Properties parms ) {
    // Jack priority for user-visible requests
    Thread.currentThread().setPriority(Thread.MAX_PRIORITY-1);
    // update arguments and determine control variables
    uri = maybeTransformRequest(uri);
    // determine the request type
    RequestType type = RequestType.requestType(uri);
    String requestName = type.requestName(uri);
    maybeLogRequest(uri, method, parms);

    try {
      // determine if we have known resource
      Request request = _requests.get(requestName);
      // if the request is not know, treat as resource request, or 404 if not found
      return request == null ? getResource(uri) : ((Request)request.clone()).serve(this,parms,type);
    } catch( Exception e ) {
      // make sure that no Exception is ever thrown out from the request
      parms.setProperty("error",e.getClass().getSimpleName()+": "+e.getMessage());
      return _http500.serve(this,parms,type);
    }
  }

  // Resource loading ----------------------------------------------------------
  // Returns the response containing the given uri with the appropriate mime type.
  private NanoHTTPD.Response getResource(String uri) {
    byte[] bytes = _cache.get(uri);
    if( bytes == null ) {
      // Try-with-resource
      try (InputStream resource = water.init.JarHash.getResource2(uri)) {
          if( resource != null ) {
            try { bytes = water.persist.Persist.toByteArray(resource); } 
            catch( IOException e ) { Log.err(e); }
            if( bytes != null ) {
              byte[] res = _cache.putIfAbsent(uri,bytes);
              if( res != null ) bytes = res; // Racey update; take what is in the _cache
            }
          }
        } catch( IOException ignore ) { }
    }
    if( bytes == null || bytes.length == 0 ) {
      // make sure that no Exception is ever thrown out from the request
      Properties parms = new Properties();
      parms.setProperty("error",uri);
      return _http404.serve(this,parms,RequestType.html);
    }
    String mime = NanoHTTPD.MIME_DEFAULT_BINARY;
    if( uri.endsWith(".css") )
      mime = "text/css";
    else if( uri.endsWith(".html") )
      mime = "text/html";
    NanoHTTPD.Response res = new NanoHTTPD.Response(NanoHTTPD.HTTP_OK,mime,new ByteArrayInputStream(bytes));
    res.addHeader("Content-Length", Long.toString(bytes.length));
    return res;
  }

}
