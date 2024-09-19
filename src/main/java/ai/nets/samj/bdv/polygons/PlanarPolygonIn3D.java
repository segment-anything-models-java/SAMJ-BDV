package ai.nets.samj.bdv.polygons;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PlanarPolygonIn3D {
	public PlanarPolygonIn3D(final AffineTransform3D viewTo3dTransform) {
		this(100, viewTo3dTransform);
	}

	public PlanarPolygonIn3D(final int expectedNoOfVertices, final AffineTransform3D viewTo3dTransform) {
		coords2D = new ArrayList<>(expectedNoOfVertices);
		transformTo3d = viewTo3dTransform.copy();
	}

	private final AffineTransform3D transformTo3d;

	public AffineTransform3D getTransformTo3d() {
		return transformTo3d.copy();
	}
	public void getTransformTo3d(AffineTransform3D fillThisOutput) {
		fillThisOutput.set(transformTo3d);
	}

	/** Corner points of the 2D polygon, which must be
	 * inside {@link PlanarPolygonIn3D#bbox2D} */
	private final List<double[]> coords2D;

	public void addPoint(int x, int y) {
		coords2D.add( new double[] {x,y} );
		updateBbox2D( x,y );
	}

	public void addPoint(double x, double y) {
		coords2D.add( new double[] {x,y} );
		updateBbox2D( x,y );
	}

	public Collection<double[]> getAllPoints() {
		return Collections.unmodifiableCollection(coords2D);
	}

	public int size() {
		return coords2D.size();
	}

	/** A interger-coords 2D box inside which exists the polygon.
	 * It is: min_x,min_y, max_x,max_y */
	private final int[] bbox2D = new int[4];

	public Interval getBbox2D() {
		return new FinalInterval(
				  new long[] {bbox2D[0],bbox2D[1]},
				  new long[] {bbox2D[2],bbox2D[3]} );
	}

	private void updateBbox2D(double x, double y) {
		bbox2D[0] = Math.min(bbox2D[0], (int)Math.floor(x));
		bbox2D[1] = Math.min(bbox2D[1], (int)Math.floor(y));
		bbox2D[2] = Math.max(bbox2D[2], (int)Math.ceil(x));
		bbox2D[3] = Math.max(bbox2D[3], (int)Math.ceil(y));
	}


	public void coordinate2D(int index, double[] outCoord) {
		double[] c = coords2D.get(index);
		outCoord[0] = c[0];
		outCoord[1] = c[1];
	}

	private final double[] auxCoord3D = new double[3];
	public void coordinate3D(int index, double[] outCoord) {
		double[] c = coords2D.get(index);
		auxCoord3D[0] = c[0];
		auxCoord3D[1] = c[1];
		transformTo3d.apply(auxCoord3D,outCoord);
	}
}
