package bdv.interactive.prompts.views;

import bdv.viewer.ViewerPanel;
import net.imglib2.realtransform.AffineTransform3D;
import java.awt.Dimension;

/**
 * This class is initiated on the current view and offers means to
 * restore this original view and, especially, parallel-plane slicing
 * views at a given distance away from the original view. Considering
 * an original view, it can be understood as a plane that slices
 * through the volume when the plane is defined by a centre (reference)
 * point and a view axis (which is normal to that plane). Parallel-plane
 * slice shares the same normal (view axis) but is positioned along the
 * axis at some another centre point (which is given distance awway from
 * the original/reference centre point).
 */
public class SlicingViews {
	public SlicingViews(final ViewerPanel currentViewerPanel) {
		resetView(currentViewerPanel);
	}

	public void resetView(final ViewerPanel currentViewerPanel) {
		currentViewerPanel.getDisplayComponent().getSize(auxDimension);
		screenCoord[0] = auxDimension.getWidth() / 2.0;
		screenCoord[1] = auxDimension.getHeight() / 2.0;
		screenCoord[2] = 0;

		currentViewerPanel.state().getViewerTransform(globalToScreenT);
		globalToScreenT.applyInverse(globalCentre, screenCoord);

		screenCoord[2] = 5.0; //sufficiently away from here - to read "more reliable" direction
		globalToScreenT.applyInverse(globalCentreShift, screenCoord);

		globalCentreShift[0] -= globalCentre[0];
		globalCentreShift[1] -= globalCentre[1];
		globalCentreShift[2] -= globalCentre[2];
		//
		double len = globalCentreShift[0] * globalCentreShift[0];
		len += globalCentreShift[1] * globalCentreShift[1];
		len += globalCentreShift[2] * globalCentreShift[2];
		len = Math.sqrt(len);
		//
		globalCentreShift[0] /= len;
		globalCentreShift[1] /= len;
		globalCentreShift[2] /= len;

		//-----
		lastUsedDelta = 0;
	}

	private final Dimension auxDimension = new Dimension();
	private final double[] screenCoord = new double[3];
	private final double[] globalCentre = new double[3];
	private final double[] globalCentreShift = new double[3];
	private final AffineTransform3D globalToScreenT = new AffineTransform3D();
	private final AffineTransform3D shiftTinGlobal = new AffineTransform3D();

	public AffineTransform3D sameViewShiftedBy(double deltaAlongViewAxis) {
		globalCentre[0] = -deltaAlongViewAxis * globalCentreShift[0];
		globalCentre[1] = -deltaAlongViewAxis * globalCentreShift[1];
		globalCentre[2] = -deltaAlongViewAxis * globalCentreShift[2];

		shiftTinGlobal.identity();
		shiftTinGlobal.setTranslation(globalCentre);

		return globalToScreenT.copy().concatenate(shiftTinGlobal);
	}

	//-----
	private int lastUsedDelta = 0;
	public AffineTransform3D nextFurtherSameView() { return sameViewShiftedBy(++lastUsedDelta); }
	public AffineTransform3D nextCloserSameView() { return sameViewShiftedBy(--lastUsedDelta); }
}
