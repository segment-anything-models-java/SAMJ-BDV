package bdv.interactive.prompts.planarshapes;

import bdv.viewer.ViewerState;
import net.imglib2.Interval;
import net.imglib2.FinalInterval;
import net.imglib2.realtransform.AffineTransform3D;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPlanarShapeIn3D {
	public AbstractPlanarShapeIn3D(final AffineTransform3D planeTo3dTransform) {
		this(100, planeTo3dTransform);
	}

	/**
	 * Constructs one planar, closed shape made of connected-segments, and defined
	 * as a sequence of 2D points-vertices (which are the end-points of the segments).
	 * The relation-ship to the original 3D space is provided in the transform.
	 *
	 * The constructor *IS NOT* making a copy of the provided transform. The caller has
	 * to make sure an immutable, at least for the duration of the validity of this object,
	 * transform is provided. Users of BigDataViewer's {@link ViewerState#getViewerTransform()}
	 * are safe as this method returns a copy of the transform.
	 */
	public AbstractPlanarShapeIn3D(final int expectedNoOfVertices, final AffineTransform3D planeTo3dTransform) {
		transformTo3d = planeTo3dTransform;
		coords2D = new ArrayList<>(expectedNoOfVertices);
	}


	protected final AffineTransform3D transformTo3d;

	public AffineTransform3D getTransformTo3d() {
		return transformTo3d.copy();
	}

	public void getTransformTo3d(AffineTransform3D fillThisOutput) {
		fillThisOutput.set(transformTo3d);
	}


	/** Corner points of the 2D polygon, which must be
	 * inside {@link AbstractPlanarShapeIn3D#bbox2D} */
	protected final List<double[]> coords2D;

	/** Return the points that make up the shape, the semantic of how they make up
	 * the shape differs in implementing classes. For example, for a rectangle this
	 * could return just two points (the diagonal), for a curve this could return
	 * points in order in which the curve goes through them. */
	public List<double[]> getAllCorners() {
		return coords2D;
	}

	public int size() {
		return coords2D.size();
	}

	/** This is a good candidate if an implementing class wants to have
	 *  a "listener"/callback when new point is added to the shape. */
	public void addPoint(double x, double y) {
		coords2D.add( new double[] {x,y} );
		updateBbox2D( x,y );
	}


	/** A interger-coords 2D box inside which exists this shape.
	 * In this order: min_x,min_y, max_x,max_y */
	protected final int[] bbox2D = new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};

	public Interval getBbox2D() {
		return new FinalInterval(
				  new long[] {bbox2D[0],bbox2D[1]},
				  new long[] {bbox2D[2],bbox2D[3]} );
	}

	/** This is a good candidate if an implementing class wants to have
	 *  a "listener"/callback when new point is added to the shape. */
	protected void updateBbox2D(double x, double y) {
		bbox2D[0] = Math.min(bbox2D[0], (int)Math.floor(x));
		bbox2D[1] = Math.min(bbox2D[1], (int)Math.floor(y));
		bbox2D[2] = Math.max(bbox2D[2], (int)Math.ceil(x));
		bbox2D[3] = Math.max(bbox2D[3], (int)Math.ceil(y));
	}


	/** Requests a particular (indexed) point in coordinates of the object's 2D plane. */
	public void coordinate2D(int index, double[] outCoord) {
		double[] c = coords2D.get(index);
		outCoord[0] = c[0];
		outCoord[1] = c[1];
	}

	/** Requests a particular (indexed) point in coordinates of the original 3D space. */
	public void coordinate3D(int index, double[] outCoord) {
		double[] c = coords2D.get(index);
		auxCoord3D[0] = c[0];
		auxCoord3D[1] = c[1];
		transformTo3d.apply(auxCoord3D,outCoord);
	}

	private final double[] auxCoord3D = new double[3];


	public boolean isPointInBbox2D(double x, double y) {
		return x >= bbox2D[0] && x <= bbox2D[2] && y >= bbox2D[1] && y <= bbox2D[3];

	}

	public abstract boolean isPointInShape(double x, double y);
}
