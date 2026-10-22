package edu.uoc.planner.extraction.internal;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Canvas page HTML → the structure the extractor needs: headings, paragraphs, lists, tables,
 * emphasis and links. Inline styles (IPO pages are mostly {@code style=}), images and scripts go.
 */
public final class HtmlCleaner {

  private static final Safelist STRUCTURE =
      Safelist.none()
          .addTags(
              "h1", "h2", "h3", "h4", "h5", "h6", "p", "br", "ul", "ol", "li", "table", "thead",
              "tbody", "tr", "th", "td", "strong", "b", "em", "i", "a")
          .addAttributes("a", "href")
          .preserveRelativeLinks(true);

  private HtmlCleaner() {}

  public static String clean(String html) {
    if (html == null || html.isBlank()) {
      return "";
    }
    Document document = Jsoup.parseBodyFragment(html);
    document.select("script, style, noscript, iframe, img, video, audio").remove();
    Document.OutputSettings output = new Document.OutputSettings().prettyPrint(false);
    // Canvas links such as "$CANVAS_OBJECT_REFERENCE$/..." are not URLs: keep them as written.
    String clean = Jsoup.clean(document.body().html(), "https://aula.uoc.edu", STRUCTURE, output);
    return compactWhitespace(clean);
  }

  /** The source indentation is kept by Jsoup and is pure token cost. No {@code <pre>} survives. */
  private static String compactWhitespace(String html) {
    return html.replace("&nbsp;", " ")
        .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
        .replaceAll(" ?\\n[\\s]*", "\n")
        .strip();
  }
}
