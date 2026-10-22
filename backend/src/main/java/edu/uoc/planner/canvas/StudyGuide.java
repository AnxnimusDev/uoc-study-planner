package edu.uoc.planner.canvas;

/**
 * Weekly study guide (GES): {@code page_url} starts with {@code ges-}. Not every course has one.
 */
public record StudyGuide(String pageUrl, String title) implements ChallengePage {}
