package com.company.changeassurance.application.port.out;

import java.util.List;

import com.company.changeassurance.domain.model.ReviewId;
import com.company.changeassurance.domain.model.StageTransition;
import com.company.changeassurance.domain.model.ToolActivity;

public interface ActivityLogRepository {

    ToolActivity saveToolActivity(ToolActivity activity);

    List<ToolActivity> findToolActivitiesByReviewId(ReviewId reviewId);

    StageTransition saveStageTransition(StageTransition transition);

    List<StageTransition> findStageTransitionsByReviewId(ReviewId reviewId);
}
