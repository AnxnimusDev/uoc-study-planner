package edu.uoc.planner.availability;

import edu.uoc.planner.availability.internal.AvailabilityProfileLoader;
import edu.uoc.planner.availability.internal.AvailabilityProperties;
import edu.uoc.planner.availability.internal.OverrideRecord;
import edu.uoc.planner.availability.internal.OverrideRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Free slots from the profile file and the stored check-in overrides. The file is read on every
 * call, so edits apply to the next plan without restarting.
 */
@Service
public class AvailabilityService {

  private final AvailabilityProperties properties;
  private final OverrideRepository overrides;
  private final FreeSlotCalculator calculator = new FreeSlotCalculator();

  AvailabilityService(AvailabilityProperties properties, OverrideRepository overrides) {
    this.properties = properties;
    this.overrides = overrides;
  }

  /**
   * @throws AvailabilityConfigException when the profile file is missing or inconsistent
   */
  public AvailabilityProfile profile() {
    return AvailabilityProfileLoader.load(properties.file());
  }

  /**
   * @param to exclusive
   */
  @Transactional(readOnly = true)
  public List<FreeSlot> freeSlots(LocalDate from, LocalDate to) {
    List<AvailabilityOverride> stored =
        overrides.findByDateGreaterThanEqualAndDateLessThanOrderByDate(from, to).stream()
            .map(OverrideRecord::toOverride)
            .toList();
    return calculator.freeSlots(profile(), stored, from, to);
  }
}
