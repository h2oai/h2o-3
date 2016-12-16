package water.api.schemas4.output;

import water.Iced;
import water.api.API;
import water.api.schemas4.OutputSchemaV4;

/**
 * Returned from {@code POST /4/sessions}
 */
public class CascadeSessionOV4 extends OutputSchemaV4<Iced, CascadeSessionOV4> {

  @API(help="Session id of the session just created. This id should be passed with " +
            "every subsequent Cascade request.")
  public String session_id;

}
