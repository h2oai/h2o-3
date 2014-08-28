package hex.schemas;

import hex.deeplearning.DeepLearningModel;
import static hex.deeplearning.DeepLearningModel.prepareDataInfo;
import water.Key;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.util.BeanUtils;

public class DeepLearningModelV2 extends ModelSchema<DeepLearningModel, DeepLearningModel.DeepLearningParameters, DeepLearningModel.DeepLearningOutput, DeepLearningModelV2> {

  public static final class DeepLearningModelOutputV2 extends ModelOutputSchema<DeepLearningModel.DeepLearningOutput, DeepLearningModelOutputV2> {
    //FIXME
    //add output fields

    @Override public DeepLearningModel.DeepLearningOutput createImpl() {
      DeepLearningModel.DeepLearningOutput impl = new DeepLearningModel.DeepLearningOutput();
      BeanUtils.copyProperties(impl, this, BeanUtils.FieldNaming.DEST_HAS_UNDERSCORES);
      return impl;
    }

    // Version&Schema-specific filling from the handler
    @Override public DeepLearningModelOutputV2 fillFromImpl( DeepLearningModel.DeepLearningOutput impl) {
      BeanUtils.copyProperties(this, impl, BeanUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);
      return this;
    }


  } // DeepLearningModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public DeepLearningV2.DeepLearningParametersV2 createParametersSchema() { return new DeepLearningV2.DeepLearningParametersV2(); }
  public DeepLearningModelOutputV2 createOutputSchema() { return new DeepLearningModelOutputV2(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public DeepLearningModel createImpl() {
    DeepLearningV2.DeepLearningParametersV2 p = ((DeepLearningV2.DeepLearningParametersV2)this.parameters);
    DeepLearningModel.DeepLearningParameters parms = p.createImpl();
    return new DeepLearningModel(Key.make() /*dest*/, null /*Job*/, p.src, prepareDataInfo(parms), parms, null);
  }

  // Version&Schema-specific filling from the impl
  @Override public DeepLearningModelV2 fillFromImpl( DeepLearningModel kmm ) {
    return super.fillFromImpl(kmm);
  }
}
