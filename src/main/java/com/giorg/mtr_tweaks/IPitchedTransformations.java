package com.giorg.mtr_tweaks;

/**
 * Interface injected into StoredMatrixTransformations to track if pitch transformations
 * have already been applied to avoid compounding.
 */
public interface IPitchedTransformations {
    boolean mtrTweaks_isPitched();
    void mtrTweaks_setPitched(boolean pitched);
}
