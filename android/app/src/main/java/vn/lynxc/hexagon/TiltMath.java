package vn.lynxc.hexagon;

/** Relative, screen-oriented tilt. No compass heading or absolute pose is retained. */
public final class TiltMath {
    private final float[] baseline = new float[9];
    private boolean calibrated;
    private static final double RANGE_RADIANS = Math.PI / 6.0;

    public void reset() {
        calibrated = false;
    }

    public float[] sample(float[] current) {
        if (current == null || current.length != 9) return null;
        for (float value : current) if (!Float.isFinite(value)) return null;
        if (!calibrated) {
            System.arraycopy(current, 0, baseline, 0, 9);
            calibrated = true;
            return new float[] {0, 0};
        }
        // Third row of baseline^T * current; relative rotations avoid the
        // upright-phone singularity of subtracting absolute Euler angles.
        double r6 = baseline[2] * current[0] + baseline[5] * current[3] + baseline[8] * current[6];
        double r7 = baseline[2] * current[1] + baseline[5] * current[4] + baseline[8] * current[7];
        double r8 = baseline[2] * current[2] + baseline[5] * current[5] + baseline[8] * current[8];
        return new float[] {
            clamp(Math.atan2(-r6, Math.hypot(r7, r8)) / RANGE_RADIANS),
            clamp(Math.atan2(r7, r8) / RANGE_RADIANS)
        };
    }

    private static float clamp(double value) {
        return (float) Math.max(-1.0, Math.min(1.0, value));
    }
}
