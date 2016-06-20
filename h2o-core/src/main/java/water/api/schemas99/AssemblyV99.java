package water.api.schemas99;


import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

/** FIXME: comments please! */
public class AssemblyV99 extends SchemaV3<Iced, AssemblyV99> {

  // input fields
  @API(help="A list of steps describing the assembly line.")
  public String[] steps;

  @API(help="Input Frame for the assembly.")
  public KeyV3.FrameKeyV3 frame;

  @API(help="The name of the file and generated class ")
  public String pojo_name;

  @API(help="The key of the Assembly object to retrieve from the DKV.")
  public String assembly_id;

  //output
  @API(help="Output of the assembly line.", direction=API.Direction.OUTPUT)
  public KeyV3.FrameKeyV3 result;

  @API(help="A Key to the fit Assembly data structure", direction=API.Direction.OUTPUT)
  public KeyV3.AssemblyKeyV3 assembly;
}