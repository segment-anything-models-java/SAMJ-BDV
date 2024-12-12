package ai.nets.samj.util;

import bdv.interactive.prompts.planarshapes.AbstractPlanarShapeIn3D;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import java.util.function.Consumer;

/**
 * A utility class (which, however, requires some aux variables for its work and,
 * for the sake of avoiding memory re-allocations, these variables are internal
 * attributes and the reason why this utility class actually needs to be instantiated
 * before using it) for rendering {@link AbstractPlanarShapeIn3D} into given image.
 *
 * @author Vladimir Ulman
 */
public class PlanarShapesRasterizer {
	final double[] coord0 = new double[3];
	final double[] coordAlongX = new double[3];
	final double[] coordAlongY = new double[3];
	final AffineTransform3D viewToImgT = new AffineTransform3D();

	final double[] dx = new double[4];
	final double[] dy = new double[4];
	final double[] coord = new double[3];

	/**
	 * Recalling that the 'shape', which is to be rendered into the provided 'img'
	 * with a pixel value 'drawValue', is fully contained in a certain plane, and
	 * confined to its bounding box (that is again fully in the plane and is even
	 * an axis-aligned box w.r.t. to the coordinate system of that plane). While
	 * it is easy to determine if a point [x,y] from the shape's plane belong to
	 * the shape, it is not advisable to iterate points on that plane as the resolution
	 * on that plane will not match to that of the provided target image.
	 *
	 * So, this method actually sweeps in the target image. For that, it projects
	 * the shape's bounding box into the target 3D space (into the target image),
	 * and establishes two (perpendicular) 3D base vectors using which the target
	 * space is swept with a running pixel from the 3D image. And then each sweeping
	 * position is taken (transformed) to the shape's plane to see if the running
	 * pixel should be labeled (draw the shape).
	 */
	public <T extends RealType<T>>
	void rasterizeIntoImg(final AbstractPlanarShapeIn3D shape,
	                      final RandomAccessible<T> img,
	                      final double drawValue) {
		if (img.numDimensions() < 2)
			throw new IllegalArgumentException("Provide 2D (or more-dimensional) image to draw the shape into.");
		final RandomAccess<T> imgRA
				  = img.numDimensions() > 2 ? img.randomAccess() : Views.addDimension(img).randomAccess();

		rasterize(shape, (pos) -> imgRA.setPositionAndGet((long)pos[0],(long)pos[1],(long)pos[2]).setReal(drawValue) );
	}

	/**
	 * See {@link PlanarShapesRasterizer#rasterizeIntoImg(AbstractPlanarShapeIn3D, RandomAccessible, double)}
	 */
	public void rasterize(final AbstractPlanarShapeIn3D shape,
	                      final Consumer<double[]> setterAtTheProvided3dPosition) {
		final Interval roi2d = shape.getBbox2D();
		coord0[0] = roi2d.min(0);
		coord0[1] = roi2d.min(1);
		coord0[2] = 0.0;
		//
		coordAlongX[0] = roi2d.max(0);
		coordAlongX[1] = roi2d.min(1);
		coordAlongX[2] = 0.0;
		//
		coordAlongY[0] = roi2d.min(0);
		coordAlongY[1] = roi2d.max(1);
		coordAlongY[2] = 0.0;

		//project the three corners into the 'img' 3D space
		shape.getTransformTo3d(viewToImgT);
		viewToImgT.apply(coord0,coord0);
		viewToImgT.apply(coordAlongX,coordAlongX);
		viewToImgT.apply(coordAlongY,coordAlongY);
		//roundToInt(coord0);
		//roundToInt(coordAlongX);
		//roundToInt(coordAlongY);

		//NB: returns (x,y,z) step vector, and number of such steps as the 4th index!
		normalizedVecFromAtoB(coord0, coordAlongX, dx);
		normalizedVecFromAtoB(coord0, coordAlongY, dy);

		for (int y = 0; y < 2*dy[3]; ++y) {
			//reusing the memory but the variable's name doesn't match its purpose!
			coordAlongY[0] = coord0[0] + (double)y * 0.5 * dy[0];
			coordAlongY[1] = coord0[1] + (double)y * 0.5 * dy[1];
			coordAlongY[2] = coord0[2] + (double)y * 0.5 * dy[2];
			final double xShift = (y & 1) > 0 ? 0.5 : 0.0;

			for (int x = 0; x < dx[3]; ++x) {
				//"sweeping position" in the original 3D image space
				coordAlongX[0] = coordAlongY[0] + ((double)x+xShift) * dx[0];
				coordAlongX[1] = coordAlongY[1] + ((double)x+xShift) * dx[1];
				coordAlongX[2] = coordAlongY[2] + ((double)x+xShift) * dx[2];

				//take back to the 2D view world/coordinates
				viewToImgT.applyInverse(coord,coordAlongX);
				//NB: coord[2] should be close to 0.0
				if (shape.isPointInShape(coord[0], coord[1])) setterAtTheProvided3dPosition.accept(coordAlongX);
			}
		}
	}

	/** Fills the (x,y,z) step vector and number of such steps as the 4th index into the last argument. */
	void normalizedVecFromAtoB(double[] A, double[] B, double[] diffVec) {
		double max = -1.0;
		for (int d = 0; d < 3; ++d) {
			diffVec[d] = B[d] - A[d];
			max = Math.max(Math.abs(diffVec[d]),max);
		}

		for (int d = 0; d < 3; ++d) diffVec[d] /= max;
		diffVec[3] = Math.ceil(max);
		//System.out.println("normalizing vec: double steps = "+max+", ceil'ed = "+diffVec[3]);
		//System.out.println("diffVec = ["+diffVec[0]+","+diffVec[1]+","+diffVec[2]+"]");
	}

	void roundToInt(double[] vec) {
		for (int i = 0; i < vec.length; ++i) vec[i] = Math.round(vec[i]);
	}

	/** A convenience shortcut: Utility shape drawer/rasterizer into the given RAI. */
	public static <T extends RealType<T>>
	void rasterizeIntoImg(final AbstractPlanarShapeIn3D shape,
	                      final RandomAccessibleInterval<T> img,
	                      final double drawValue) {
		new PlanarShapesRasterizer().rasterizeIntoImg(shape, Views.extendValue(img,0.0), drawValue);
	}
}
