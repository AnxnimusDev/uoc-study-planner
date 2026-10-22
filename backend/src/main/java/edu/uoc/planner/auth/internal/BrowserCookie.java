package edu.uoc.planner.auth.internal;

/** A cookie captured from the login browser. {@link #toString()} hides the value. */
record BrowserCookie(String name, String value) {

  @Override
  public String toString() {
    return "BrowserCookie[name=" + name + ", value=***]";
  }
}
