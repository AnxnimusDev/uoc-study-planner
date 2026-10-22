package edu.uoc.planner.canvas;

/** Any other page of the module, e.g. "Plantejament i competències", used as extraction context. */
public record OtherPage(String pageUrl, String title) implements ChallengePage {}
