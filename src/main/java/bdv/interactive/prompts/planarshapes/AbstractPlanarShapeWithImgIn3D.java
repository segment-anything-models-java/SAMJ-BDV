package bdv.interactive.prompts.planarshapes;

import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.realtransform.AffineTransform3D;

public abstract class AbstractPlanarShapeWithImgIn3D <T extends RealType<T> & NativeType<T>> extends AbstractPlanarShapeIn3D {
	public AbstractPlanarShapeWithImgIn3D(final int expectedNoOfVertices, final Img<T> viewImage2D, final AffineTransform3D planeTo3dTransform) {
		super(expectedNoOfVertices, planeTo3dTransform);
		supportingViewImage2D = viewImage2D;
	}

	public AbstractPlanarShapeWithImgIn3D(final Img<T> viewImage2D, final AffineTransform3D planeTo3dTransform) {
		super(planeTo3dTransform);
		supportingViewImage2D = viewImage2D;
	}

	protected final Img<T> supportingViewImage2D;

	public Img<T> getViewImage2D() {
		return supportingViewImage2D;
	}
}
