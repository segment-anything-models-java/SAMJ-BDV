package bdv.interactive.prompts.planarshapes;

import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class PlanarRectangleIn3D <T extends RealType<T> & NativeType<T>> extends AbstractPlanarShapeWithImgIn3D<T> {
	public PlanarRectangleIn3D(final Img<T> viewImage2D, final AffineTransform3D planeTo3dTransform) {
		super(2, viewImage2D, planeTo3dTransform);
	}

	public void setDiagonal(double min_x, double min_y, double max_x, double max_y) {
		addPoint(min_x, min_y);
		addPoint(max_x, max_y);
	}

	public void resetDiagonal(double min_x, double min_y, double max_x, double max_y) {
		bbox2D[0] = Integer.MAX_VALUE;
		bbox2D[1] = Integer.MAX_VALUE;
		bbox2D[2] = Integer.MIN_VALUE;
		bbox2D[3] = Integer.MIN_VALUE;
		coords2D.clear();
		this.setDiagonal(min_x,min_y, max_x,max_y);
	}

	@Override
	public boolean isPointInShape(double x, double y) {
		return isPointInBbox2D(x,y);
	}
}