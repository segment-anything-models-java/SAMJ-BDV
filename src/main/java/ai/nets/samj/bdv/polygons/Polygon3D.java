package ai.nets.samj.bdv.polygons;

import net.imglib2.realtransform.AffineTransform3D;
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
 * The polygon knows its way back to the 2D plane, and forth as well. That said,
 * 3D affine transforms between 3D and [x,y,0] coordinates (that are technically
 * 2D coordinates) is available for both directions.
 *
 * @author Vladimir Ulman
 */
public class Polygon3D {
	public static class Builder {
		public Builder(final AffineTransform3D transform3DtoEffective2D) {
			this(1000, transform3DtoEffective2D);
		}

		public Builder(final int expectedNoOfVertices, final AffineTransform3D transform3DtoEffective2D) {
			XYZs = new ArrayList<>(expectedNoOfVertices);
			this.transform3Dto2D = transform3DtoEffective2D;
		}

		private final List<double[]> XYZs;
		private final AffineTransform3D transform3Dto2D;
		private double toleranceDelta = 0.001;

		public void setToleranceFor2D(double newToleranceDelta) {
			this.toleranceDelta = newToleranceDelta;
		}

		public void addVertex(double[] coords3D) {
			addVertex(coords3D[0],coords3D[1],coords3D[2]);
		}

		public void addVertex(double x, double y, double z) {
			double[] c = new double[] {x,y,z}; //since List.add() isn't making own copy, we have to do one here

			transform3Dto2D.apply(c,auxVec);
			if (auxVec[2] < -toleranceDelta || auxVec[2] > toleranceDelta) {
				throw new IllegalArgumentException("Input coord ["+x+","+y+","+z
						+"] maps to ["+auxVec[0]+","+auxVec[1]+","+auxVec[2]
						+"], the third mapped coord is outside the 2D tolerance ("+toleranceDelta+")");
			}

			XYZs.add(c);
		}

		private final double[] auxVec = new double[3];

		public Polygon3D build() {
			return new Polygon3D(XYZs, transform3Dto2D.copy()); //XYZs hold copies already, copy() also the transform
		}
	}


	private Polygon3D(final List<double[]> XYZs, final AffineTransform3D transform3DtoEffective2D) {
		this.coords = XYZs;
		this.transform3Dto2D = transform3DtoEffective2D;
	}

	private final List<double[]> coords;
	private final AffineTransform3D transform3Dto2D;
	private final double[] forExportCoord = new double[3];

	public int size() {
		return coords.size();
	}

	public double[] coordinate3D(int index) {
		if (index < 0 || index >= coords.size()) return null;

		double[] c = coords.get(index);
		forExportCoord[0] = c[0];
		forExportCoord[1] = c[1];
		forExportCoord[2] = c[2];
		return forExportCoord;
	}

	public double[] coordinate2D(int index) {
		if (index < 0 || index >= coords.size()) return null;

		double[] c = coords.get(index);
		transform3Dto2D.apply(c,forExportCoord);
		return forExportCoord;
	}
}
