package water.api.schemas4.input;

import water.Iced;
import water.api.API;
import water.api.schemas4.InputSchemaV4;

/**
 * Input schema for the {@code "GET /4/jobs/{job_id}"} endpoint.
 */
public class JobIV4 extends InputSchemaV4<Iced, JobIV4> {

  @API(help="Id of the job to fetch.")
  public String job_id;

}
