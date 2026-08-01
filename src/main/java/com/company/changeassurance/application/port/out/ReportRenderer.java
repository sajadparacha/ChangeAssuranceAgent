package com.company.changeassurance.application.port.out;

import com.company.changeassurance.domain.model.ChangeReview;

/**
 * Renders the final assurance report for API/UI consumption. Implementation in later phases.
 */
public interface ReportRenderer {

    String renderMarkdown(ChangeReview review);

    Object renderStructured(ChangeReview review);
}
