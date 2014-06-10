
import water.*;
import water.util.DocGen.HTML;
import water.api.*;

public class SlowJobV2 extends Schema<SlowJob,SlowJobV2> {

  // Input fields
  @API(help="work",validation="/*this input is required*/")
  int work;

  // Output fields
  @API(help="Job Key")
  Key key;

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public SlowJobV2 fillInto( SlowJob h ) {
    h._work = work;
    return this;
  }

  // Version&Schema-specific filling from the handler
  @Override public SlowJobV2 fillFrom( SlowJob h ) {
    key = h._job._key;
    return this;
  }

  @Override public HTML writeHTML_impl( HTML ab ) {
    ab.title("Slow Job Started");
    String url = JobPollV2.link(key);
    return ab.href("Poll",url,url);
  }

}
