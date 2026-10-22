package edu.uoc.planner.canvas;

/** A page of a challenge module. The body is fetched separately, only when it changes. */
public sealed interface ChallengePage permits Statement, StudyGuide, OtherPage {

  String pageUrl();

  String title();
}
