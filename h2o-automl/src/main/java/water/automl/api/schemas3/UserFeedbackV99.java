package water.automl.api.schemas3;

import ai.h2o.automl.AutoML;
import ai.h2o.automl.UserFeedback;
import water.api.API;
import water.api.Schema;

public class UserFeedbackV99 extends Schema<UserFeedback, UserFeedbackV99> {
  @API(help="The AutoML ID for these events", direction=API.Direction.INOUT)
  public AutoML.AutoMLKeyV3 automl_id;

  @API(help="", direction=API.Direction.OUTPUT)
  public UserFeedbackEventV99[] feedback_events;

  @Override public UserFeedbackV99 fillFromImpl(UserFeedback userFeedback) {
    super.fillFromImpl(userFeedback, new String[] { "automl_id", "feedback_events" });

    if (null != userFeedback.autoML) {
      this.automl_id = new AutoML.AutoMLKeyV3(userFeedback.autoML._key);
    }

    if (null != userFeedback.feedbackEvents) {
      this.feedback_events = new UserFeedbackEventV99[userFeedback.feedbackEvents.length];
      for (int i = 0; i < userFeedback.feedbackEvents.length; i++)
        this.feedback_events[i] = new UserFeedbackEventV99().fillFromImpl(userFeedback.feedbackEvents[i]);
    }

    return this;
  }
}
