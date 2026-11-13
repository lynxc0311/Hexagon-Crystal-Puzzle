package vn.lynxc.hexagon;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TiltMathTest {
    private static final float EPS = .0002f;
    private static float[] identity() { return new float[]{1,0,0,0,1,0,0,0,1}; }
    private static float[] rx(double degrees) {
        float c=(float)Math.cos(Math.toRadians(degrees)),s=(float)Math.sin(Math.toRadians(degrees));
        return new float[]{1,0,0,0,c,-s,0,s,c};
    }
    private static float[] ry(double degrees) {
        float c=(float)Math.cos(Math.toRadians(degrees)),s=(float)Math.sin(Math.toRadians(degrees));
        return new float[]{c,0,s,0,1,0,-s,0,c};
    }
    private static float[] multiply(float[] a,float[] b) {
        float[] result=new float[9];
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)for(int k=0;k<3;k++)result[i*3+j]+=a[i*3+k]*b[k*3+j];
        return result;
    }
    @Test public void neutralAndTwoAxes() {
        TiltMath math=new TiltMath();
        assertArrayEquals(new float[]{0,0},math.sample(identity()),EPS);
        assertArrayEquals(new float[]{0,1f/3},math.sample(rx(10)),EPS);
        assertArrayEquals(new float[]{1,0},math.sample(ry(30)),EPS);
        assertArrayEquals(new float[]{-1,0},math.sample(ry(-60)),EPS);
    }
    @Test public void relativeToComfortableHoldingPose() {
        TiltMath math=new TiltMath();float[] baseline=rx(80);
        math.sample(baseline);
        assertArrayEquals(new float[]{.5f,0},math.sample(multiply(baseline,ry(15))),EPS);
        math.reset();
        assertArrayEquals(new float[]{0,0},math.sample(ry(60)),EPS);
    }
    @Test public void yawDoesNotTiltAndInvalidSamplesAreIgnored() {
        TiltMath math=new TiltMath();
        assertNull(math.sample(null));assertNull(math.sample(new float[4]));
        float[] bad=identity();bad[0]=Float.NaN;assertNull(math.sample(bad));
        math.sample(identity());
        assertArrayEquals(new float[]{0,0},math.sample(new float[]{0,-1,0,1,0,0,0,0,1}),EPS);
        assertArrayEquals(new float[]{0,1},math.sample(rx(75)),EPS);
    }
}
