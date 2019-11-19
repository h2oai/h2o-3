package hex;

import water.Iced;

public abstract class ModelBuilderListener<D extends Iced> extends Iced<D> {
  /**
   * Callback for successfully finished model builds
   *
   * @param model Model built
   */
  abstract void onModelSuccess(Model model);

  /**
   * Callback for failed model builds
   *
   * @param cause      An instance of {@link Throwable} - cause of failure
   * @param parameters An instance of Model.Parameters used in the attempt to build the model
   */
  abstract void onModelFailure(Throwable cause, Model.Parameters parameters);


}
