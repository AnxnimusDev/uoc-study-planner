package edu.uoc.planner.canvas;

/** The challenge statement: {@code page_url} starts with {@code enunciat-de-lactivitat}. */
public record Statement(String pageUrl, String title) implements ChallengePage {}
