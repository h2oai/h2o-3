package testframework.fivejvm;

import testframework.multinode.MultiNodeSetup;

public class FiveJvmSetup extends MultiNodeSetup {
  final static boolean multiJvm = true;
  public FiveJvmSetup() {
    super(5, multiJvm);
  }
}
