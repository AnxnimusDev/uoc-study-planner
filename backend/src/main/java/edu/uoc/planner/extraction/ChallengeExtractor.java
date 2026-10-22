package edu.uoc.planner.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uoc.planner.extraction.internal.ExtractionPrompts;
import edu.uoc.planner.extraction.internal.SpecModel;
import edu.uoc.planner.extraction.internal.SpecModel.ModelReply;
import edu.uoc.planner.extraction.internal.SpecValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Challenge statement → validated {@link ChallengeSpec} (docs/design.md, section 6). One retry with
 * the validation errors; after that, or on a refusal, the challenge needs review. Extracts
 * requirements only (ADR 0003).
 */
@Service
public final class ChallengeExtractor {

  private static final Logger log = LoggerFactory.getLogger(ChallengeExtractor.class);

  private final SpecModel model;
  private final SpecValidator validator;
  private final ObjectMapper mapper = new ObjectMapper();

  public ChallengeExtractor(SpecModel model, SpecValidator validator) {
    this.model = model;
    this.validator = validator;
  }

  /**
   * @throws ExtractionUnavailableException when Claude cannot be asked; try again later
   */
  public ExtractionResult extract(ExtractionInput input) {
    String userMessage = ExtractionPrompts.user(input);
    String hash = hash(userMessage);
    Set<String> refs =
        input.deliverables().stream()
            .map(ExtractionInput.Deliverable::ref)
            .collect(Collectors.toUnmodifiableSet());

    ModelReply first = model.complete(userMessage);
    if (first.refused()) {
      return refused(first, hash);
    }
    List<String> errors = problems(first, refs);
    if (errors.isEmpty()) {
      return extracted(first, hash);
    }
    log.info("Extraction invalid ({} problems), retrying once", errors.size());

    ModelReply second = model.complete(ExtractionPrompts.retry(userMessage, first.text(), errors));
    if (second.refused()) {
      return refused(second, hash);
    }
    List<String> retryErrors = problems(second, refs);
    if (retryErrors.isEmpty()) {
      return extracted(second, hash);
    }
    log.info("Extraction still invalid after the retry ({} problems)", retryErrors.size());
    return new ExtractionResult.NeedsReview(retryErrors, second.text(), second.model(), hash);
  }

  /** Hash of the normalized input (cleaned HTML and deliverables): same value, same extraction. */
  public String sourceHash(ExtractionInput input) {
    return hash(ExtractionPrompts.user(input));
  }

  private List<String> problems(ModelReply reply, Set<String> refs) {
    if (reply.truncated()) {
      return List.of("Respuesta truncada (stop_reason max_tokens)");
    }
    return validator.validate(reply.text(), refs);
  }

  private ExtractionResult extracted(ModelReply reply, String hash) {
    try {
      ChallengeSpec spec = mapper.readValue(reply.text(), ChallengeSpec.class);
      return new ExtractionResult.Extracted(spec, reply.text(), reply.model(), hash);
    } catch (JsonProcessingException e) {
      // Already validated against the schema: a failure here is a mismatch with ChallengeSpec.
      throw new IllegalStateException("Valid spec does not map to ChallengeSpec", e);
    }
  }

  private static ExtractionResult refused(ModelReply reply, String hash) {
    log.info("Extraction refused by {} ({})", reply.model(), reply.refusalCategory());
    return new ExtractionResult.NeedsReview(
        List.of("El modelo se ha negado a responder (refusal: " + reply.refusalCategory() + ")"),
        reply.text(),
        reply.model(),
        hash);
  }

  private static String hash(String text) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
