package water.persist;

import org.apache.http.*;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.*;
import water.parser.ParseDataset;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.Assert.*;

public class PersistHTTPTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void importFilesEager() throws Exception {
    try {
      Scope.enter();

      Frame f = Scope.track(parseTestFile(Key.make("prostate.hex"), "smalldata/prostate/prostate.csv"));

      final String localUrl = H2O.getURL(H2O.ARGS.jks != null ? "https" : "http") + "/3/DownloadDataset.bin?frame_id=" + f._key.toString();

      PersistHTTP p = new PersistHTTP();

      assertEquals(-1L, p.useLazyLoad(URI.create(localUrl))); // H2O doesn't support byte-ranges

      ArrayList<String> files = new ArrayList<>();
      ArrayList<String> keys = new ArrayList<>();
      ArrayList<String> fails = new ArrayList<>();
      ArrayList<String> dels = new ArrayList<>();
      p.importFiles(localUrl, null, files, keys, fails, dels);

      assertTrue(fails.isEmpty());
      assertTrue(dels.isEmpty());
      assertEquals(Collections.singletonList(localUrl), files);
      assertEquals(Collections.singletonList(localUrl), keys);

      Key<Frame> k = Key.make(localUrl);
      Frame imported = k.get();
      assertEquals(1, imported.numCols());
      assertTrue(imported.vec(0) instanceof UploadFileVec); // Dataset was uploaded eagerly

      Key<Frame> out = Key.make();
      Frame parsed = Scope.track(ParseDataset.parse(out, k));

      assertBitIdentical(f, parsed);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void importFilesLazy() throws Exception {
    try {
      Scope.enter();

      Frame f = Scope.track(parseTestFile(Key.make("prostate.hex"), "smalldata/prostate/prostate.csv"));

      final String remoteUrl = "https://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv";
      final long expectedSize = 9254L;

      PersistHTTP p = new PersistHTTP();

      assertEquals(expectedSize, p.useLazyLoad(URI.create(remoteUrl))); // S3 supports byte-ranges

      ArrayList<String> files = new ArrayList<>();
      ArrayList<String> keys = new ArrayList<>();
      ArrayList<String> fails = new ArrayList<>();
      ArrayList<String> dels = new ArrayList<>();
      p.importFiles(remoteUrl, null, files, keys, fails, dels);


      Key<Frame> k = Key.make(remoteUrl);
      Frame imported = Scope.track(k.get());
      assertEquals(1, imported.numCols());
      assertTrue(imported.vec(0) instanceof HTTPFileVec);

      ((HTTPFileVec) imported.vec(0)).setChunkSize(imported, 1024);
      assertEquals(10, imported.vec(0).nChunks());

      Key<Frame> out = Key.make();
      Frame parsed = Scope.track(ParseDataset.parse(out, k));

      assertBitIdentical(f, parsed);
      assertEquals(imported.vec(0).nChunks(), f.anyVec().nChunks());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testPubdev5847ParseCompressed() {
    try {
      Scope.enter();

      final String remoteUrl = "https://raw.githubusercontent.com/h2oai/h2o-3/master/h2o-r/h2o-package/inst/extdata/australia.csv";

      PersistHTTP p = new PersistHTTP();

      ArrayList<String> files = new ArrayList<>();
      ArrayList<String> keys = new ArrayList<>();
      ArrayList<String> fails = new ArrayList<>();
      ArrayList<String> dels = new ArrayList<>();
      p.importFiles(remoteUrl, null, files, keys, fails, dels);


      Key<Frame> k = Key.make(remoteUrl);
      Frame imported = Scope.track(k.get());
      assertEquals(1, imported.numCols());
      assertTrue(imported.vec(0) instanceof HTTPFileVec);

      Key<Frame> out = Key.make();
      Frame parsed = Scope.track(ParseDataset.parse(out, k));

      assertEquals(251, parsed.numRows());
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void testReadContentLength() {
    HttpResponse r = mock(HttpResponse.class);
    HttpEntity e = mock(HttpEntity.class);
    when(e.getContentLength()).thenReturn(42L);
    when(r.getEntity()).thenReturn(e);

    assertEquals(42L, PersistHTTP.readContentLength(r));
  }

  @Test
  public void testReadContentLengthFromRange() {
    HttpResponse r = mock(HttpResponse.class);
    HttpEntity e = mock(HttpEntity.class);
    Header h =mock(Header.class);
    when(e.getContentLength()).thenReturn(-1L);
    when(r.getEntity()).thenReturn(e);
    when(r.getFirstHeader(HttpHeaders.CONTENT_RANGE)).thenReturn(h);
    when(h.getValue()).thenReturn("bytes 7-48/anything");

    assertEquals(42L, PersistHTTP.readContentLength(r));
  }

  @Test
  public void testIsCompressed() {
    assertFalse(PersistHTTP.isCompressed(mockResponse(null)));
    assertFalse(PersistHTTP.isCompressed(mockResponse("text/csv")));
    assertTrue(PersistHTTP.isCompressed(mockResponse("application/zip")));
    assertTrue(PersistHTTP.isCompressed(mockResponse("application/gzip")));
    assertTrue(PersistHTTP.isCompressed(mockResponse("application/GZip"))); // case insensitive
  }
  
  private HttpResponse mockResponse(final String contentType) {
    HttpResponse r = mock(HttpResponse.class);
    if (contentType != null) {
      when(r.getFirstHeader(HttpHeaders.CONTENT_TYPE)).thenReturn(new Header() {
        @Override
        public String getName() {
          return HttpHeaders.CONTENT_TYPE;
        }

        @Override
        public String getValue() {
          return contentType;
        }

        @Override
        public HeaderElement[] getElements() throws ParseException {
          return new HeaderElement[0];
        }
      });
    }
    return r;
  }
  
}
