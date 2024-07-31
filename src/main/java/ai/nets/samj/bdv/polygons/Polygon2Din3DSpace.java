package ai.nets.samj.bdv.polygons;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable representation of a 2D (planar) polygon in 3D volume,
 * represented as a list of 2D vertices that, in order, make up the polygon.
 * All vertices are guaranteed to lay inside a certain rectangle in a plane,
 * the rectangle acts as a bounding box but it needs not be necessarily the
 * minimal/tightest bounding box. The mapping transform from the plane to an
 * implicit 3D volume is provided too.
 *
 * The idea is that one can "work" with the polygon in (easier) 2D plane,
 * e.g. determine all pixels that are inside this polygon sweeping only
 * within the provided bounds (the bounding box); and then possibly bring
 * the 2D coordinates into some 3D, e.g. if the polygon was part of some
 * cutting plane.
 *
 * The first and last vertices are assumed *not* to be the same -- the closing
 * segment is implicit between the last and first vertex of the polygon,
 * just as usually.
 *
 * @author Vladimir Ulman
 */
public class Polygon2Din3DSpace {
	public static class Builder {
		public Builder() {
			this(1000);
		}

		public Builder(int expectedNoOfVertices) {
			XYs = new ArrayList<>(2*expectedNoOfVertices);
		}

		final private List<Long> XYs;
		private long min_x,min_y, max_x,max_y;
		private boolean min_set = false, max_set = false;
		private AffineTransform3D xyTo3D;

		public void addVertex(long x, long y) {
			XYs.add(x);
			XYs.add(y);
		}

		public void setBoundingBoxMinCorner(long x, long y) {
			min_x = x;
			min_y = y;
			min_set = true;
		}

		public void setBoundingBoxMaxCorner(long x, long y) {
			max_x = x;
			max_y = y;
			max_set = true;
		}

		public void set2Dto3Dtranform(final AffineTransform3D transform) {
			xyTo3D = transform.copy();
		}

		public Polygon2Din3DSpace build() {
			if (!min_set || !max_set) {
				throw new IllegalArgumentException("Min or max corner of a bounding box wasn't provided.");
			}
			if (xyTo3D == null) {
				throw new IllegalArgumentException("Transform that maps the polygon to 3D space wasn't provided.");
			}

			return new Polygon2Din3DSpace(XYs,xyTo3D,min_x,min_y,max_x,max_y);
		}
	}


	private Polygon2Din3DSpace(final List<Long> XYs,
	                           final AffineTransform3D tranform,
	                           final long min_x, final long min_y,
	                           final long max_x, final long max_y) {
		final int cnt = XYs.size() / 2;
		coords = new ArrayList<>(cnt);
		for (int i = 0; i < cnt; ++i) {
			long[] c = new long[2];
			c[0] = XYs.get(2*i   );
			c[1] = XYs.get(2*i +1);
			coords.add(c);
		}

		transformTo3D = tranform;
		sweeping2dInterval = new FinalInterval(new long[] {min_x,min_y}, new long[] {max_x,max_y});
	}

	final private List<long[]> coords;
	final private long[]   forExportCoord2D = new long[2];
	final private double[] forExportCoord3D = new double[3];

	final private AffineTransform3D transformTo3D;
	final private Interval sweeping2dInterval;

	public AffineTransform3D getTransformTo3D() {
		return transformTo3D.copy();
	}

	public Interval getSweeping2dInterval() {
		return sweeping2dInterval;
	}

	public int size() {
		return coords.size();
	}

	public long[] coordinateLocal2D(int index) {
		if (index < 0 || index >= coords.size()) return null;

		long[] c = coords.get(index);
		forExportCoord2D[0] = c[0];
		forExportCoord2D[1] = c[1];
		return forExportCoord2D;
	}

	public double[] coordinate3D(int index) {
		if (index < 0 || index >= coords.size()) return null;

		long[] c = coords.get(index);
		forExportCoord3D[0] = c[0];
		forExportCoord3D[1] = c[1];
		forExportCoord3D[2] = 0.0;
		transformTo3D.apply(forExportCoord3D,forExportCoord3D);
		return forExportCoord3D;
	}
}
