package ai.nets.samj.bdv.polygons;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable representation of a 2D (planar) polygon in 3D volume,
 * represented as a list of 3D vertices that, in order, make up the polygon.
 *
 * The first and last vertices are assumed *not* to be the same -- the closing
 * segment is implicit between the last and first vertex of the polygon,
 * just as usually.
 *
 * @author Vladimir Ulman
 */
public class Polygon3D {
	public static class Builder {
		public Builder() {
			this(1000);
		}

		public Builder(int expectedNoOfVertices) {
			XYZs = new ArrayList<>(3*expectedNoOfVertices);
		}

		final private List<Double> XYZs;

		public void addVertex(double x, double y, double z) {
			XYZs.add(x);
			XYZs.add(y);
			XYZs.add(z);
		}

		public Polygon3D build() {
			return new Polygon3D(XYZs);
		}
	}


	private Polygon3D(final List<Double> XYZs) {
		final int cnt = XYZs.size() / 3;
		coords = new ArrayList<>(cnt);
		for (int i = 0; i < cnt; ++i) {
			double[] c = new double[3];
			c[0] = XYZs.get(3*i   );
			c[1] = XYZs.get(3*i +1);
			c[2] = XYZs.get(3*i +2);
			coords.add(c);
		}
	}


	final private List<double[]> coords;
	final private double[] forExportCoord = new double[3];

	public int size() {
		return coords.size();
	}

	public double[] coordinate(int index) {
		if (index < 0 || index >= coords.size()) return null;

		double[] c = coords.get(index);
		forExportCoord[0] = c[0];
		forExportCoord[1] = c[1];
		forExportCoord[2] = c[2];
		return forExportCoord;
	}
}
