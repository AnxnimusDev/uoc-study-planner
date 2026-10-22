package edu.uoc.planner.scheduling;

import edu.uoc.planner.canvas.CanvasCatalog;
import edu.uoc.planner.canvas.ChallengeStatus;
import edu.uoc.planner.canvas.Deliverable;
import edu.uoc.planner.canvas.StoredChallenge;
import edu.uoc.planner.extraction.ChallengeExtractor;
import edu.uoc.planner.extraction.ExtractionInput;
import edu.uoc.planner.extraction.ExtractionResult;
import edu.uoc.planner.extraction.ExtractionUnavailableException;
import edu.uoc.planner.extraction.SpecStore;
import edu.uoc.planner.extraction.StoredSpec;
import edu.uoc.planner.scheduling.ExtractionReport.Item;
import edu.uoc.planner.scheduling.ExtractionReport.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Flow step 3 (docs/design.md, section 4): extract the challenges whose statement is new or
 * changed. Claude is only asked when the source hash differs from the last attempt, valid or not,
 * and never over a spec the student corrected.
 */
@Service
public class ExtractionRun {

  private static final Logger log = LoggerFactory.getLogger(ExtractionRun.class);

  private final CanvasCatalog catalog;
  private final ChallengeExtractor extractor;
  private final SpecStore specs;

  ExtractionRun(CanvasCatalog catalog, ChallengeExtractor extractor, SpecStore specs) {
    this.catalog = catalog;
    this.extractor = extractor;
    this.specs = specs;
  }

  public ExtractionReport run() {
    List<Item> items = new ArrayList<>();
    for (StoredChallenge challenge : catalog.selectedChallenges()) {
      if (challenge.statementHtml() == null || challenge.statementHtml().isBlank()) {
        items.add(item(challenge, Outcome.NO_STATEMENT));
        continue;
      }
      ExtractionInput input = input(challenge);
      Optional<StoredSpec> latest = specs.latest(challenge.id());
      String hash = extractor.sourceHash(input);
      if (latest.isPresent() && latest.get().sourceHash().equals(hash)) {
        items.add(item(challenge, Outcome.UNCHANGED));
        continue;
      }
      if (latest.isPresent() && latest.get().userEdited()) {
        items.add(item(challenge, Outcome.EDITED_BY_STUDENT));
        continue;
      }
      ExtractionResult result;
      try {
        result = extractor.extract(input);
      } catch (ExtractionUnavailableException e) {
        log.warn("Extraction stopped: {}", e.getMessage());
        return new ExtractionReport(items, e.getMessage());
      }
      specs.save(challenge.id(), result);
      boolean valid = result instanceof ExtractionResult.Extracted;
      catalog.updateStatus(
          challenge.id(), valid ? ChallengeStatus.EXTRACTED : ChallengeStatus.NEEDS_REVIEW);
      items.add(item(challenge, valid ? Outcome.EXTRACTED : Outcome.NEEDS_REVIEW));
    }
    return new ExtractionReport(items, null);
  }

  private static ExtractionInput input(StoredChallenge challenge) {
    List<ExtractionInput.Deliverable> deliverables =
        challenge.deliverables().stream()
            .map(
                (Deliverable d) ->
                    new ExtractionInput.Deliverable(
                        "assignment:" + d.canvasAssignmentId(),
                        d.name(),
                        d.dueAt(),
                        d.points(),
                        d.countsForGrade()))
            .toList();
    return new ExtractionInput(
        challenge.courseId(),
        challenge.name(),
        challenge.statementHtml(),
        challenge.approachHtml(),
        challenge.studyGuideHtml(),
        null,
        deliverables);
  }

  private static Item item(StoredChallenge challenge, Outcome outcome) {
    return new Item(challenge.id(), challenge.courseCode(), challenge.name(), outcome);
  }
}
