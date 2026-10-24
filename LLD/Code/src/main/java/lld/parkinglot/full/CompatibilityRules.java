package lld.parkinglot.full;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;


/**
 * Which slot types a vehicle may use, in order of preference.
 * An EV prefers a charging slot and falls back to a normal car slot.
 */
public final class CompatibilityRules {

    private final Map<VehicleType, List<SlotType>> preferences = new EnumMap<>(VehicleType.class);

    public static CompatibilityRules defaults() {
        return new CompatibilityRules()
                .allow(VehicleType.TRUCK, SlotType.TRUCK)
                .allow(VehicleType.BIKE, SlotType.BIKE)
                .allow(VehicleType.CAR, SlotType.CAR)
                .allow(VehicleType.EV_CAR, SlotType.EV_CHARGING, SlotType.CAR);
    }

    public CompatibilityRules allow(VehicleType vehicle, SlotType... inPreferenceOrder) {
        preferences.put(vehicle, List.of(inPreferenceOrder));
        return this;
    }

    public List<SlotType> slotTypesFor(VehicleType vehicle) {
        List<SlotType> types = preferences.get(vehicle);
        if (types == null) {
            throw new ValidationException("No slot type accepts " + vehicle);
        }
        return types;
    }

    /** Lower is better; -1 means the slot type is not allowed for this vehicle. */
    public int rank(VehicleType vehicle, SlotType slot) {
        return slotTypesFor(vehicle).indexOf(slot);
    }
}
