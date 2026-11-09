package lld.parkinglot.full.rules;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import lld.parkinglot.full.exception.ValidationException;
import lld.parkinglot.full.model.SlotType;
import lld.parkinglot.full.model.VehicleType;

/**
 * E3: which slot types a vehicle may use, in order of preference, and how many vehicles a slot
 * holds. Defaults: a vehicle may use its own size or larger; when bike sharing is on (busy
 * hours), a car slot holds up to {@code bikesPerCarSlot} bikes.
 */
public final class CompatibilityRules {

    private final Map<VehicleType, List<SlotType>> preferences = new EnumMap<>(VehicleType.class);
    private final int bikesPerCarSlot;
    private volatile boolean bikeSharing;

    public CompatibilityRules(int bikesPerCarSlot) {
        ValidationException.require(bikesPerCarSlot >= 1, "Bikes per car slot must be at least 1");
        this.bikesPerCarSlot = bikesPerCarSlot;
    }

    public static CompatibilityRules defaults() {
        return new CompatibilityRules(4)
                .allow(VehicleType.BIKE, SlotType.BIKE, SlotType.CAR, SlotType.TRUCK)
                .allow(VehicleType.CAR, SlotType.CAR, SlotType.TRUCK)
                .allow(VehicleType.TRUCK, SlotType.TRUCK);
    }

    /** V0 behaviour: each vehicle only in its own slot type. */
    public static CompatibilityRules strict() {
        return new CompatibilityRules(1)
                .allow(VehicleType.BIKE, SlotType.BIKE)
                .allow(VehicleType.CAR, SlotType.CAR)
                .allow(VehicleType.TRUCK, SlotType.TRUCK);
    }

    public CompatibilityRules allow(VehicleType vehicle, SlotType... inPreferenceOrder) {
        preferences.put(vehicle, List.of(inPreferenceOrder));
        return this;
    }

    /** Lower is better; -1 means not allowed. */
    public int rank(VehicleType vehicle, SlotType slot) {
        List<SlotType> allowed = preferences.get(vehicle);
        if (allowed == null) {
            throw new ValidationException("No slot type accepts " + vehicle);
        }
        return allowed.indexOf(slot);
    }

    /** How many vehicles of this type the slot may hold right now. */
    public int capacity(SlotType slot, VehicleType vehicle) {
        return bikeSharing && slot == SlotType.CAR && vehicle == VehicleType.BIKE ? bikesPerCarSlot : 1;
    }

    public void setBikeSharing(boolean enabled) {
        bikeSharing = enabled;
    }

    public boolean bikeSharing() {
        return bikeSharing;
    }
}
