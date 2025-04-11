package bdv.interactive.prompts.views;

import bdv.viewer.ViewerPanel;
import bdv.viewer.animate.AbstractTransformAnimator;
import bdv.viewer.animate.SimilarityTransformAnimator;
import net.imglib2.realtransform.AffineTransform3D;
import java.awt.Point;
import java.awt.Dimension;

/**
 * This class is reset with the current view and offers means to
 * restore this original view and, especially, side views from angles
 * parallel to screen/display x- and y-axes (because the current view
 * can be considered basically as if along the screen/display z-axis).
 * Reaching the side views, as well as restoring the original view, is
 * animated and happens with respect to the mouse cursor (just like it
 * normally happens with rotations in BDV) whose position is remembered
 * during the class (a session if you will) reset.
 */
public class SideViews {
	public SideViews(final ViewerPanel currentViewerPanel) {
		resetView(currentViewerPanel);
	}

	public void resetView(final ViewerPanel currentViewerPanel) {
		mouseScreenPos = currentViewerPanel.getMousePosition();
		if (mouseScreenPos == null) {
			System.out.println("BDV: Mouse not over the view panel, using panel's centre instead.");
			currentViewerPanel.getDisplayComponent().getSize(auxScreenDimension);
			mouseScreenPos = new Point( auxScreenDimension.width/2, auxScreenDimension.height/2);
		}
		currentViewerPanel.state().getViewerTransform(globalToScreenT_initialViewT);
	}

	private Point mouseScreenPos;
	private final Dimension auxScreenDimension = new Dimension();
	private final AffineTransform3D globalToScreenT_initialViewT = new AffineTransform3D();
	private final AffineTransform3D rotator = new AffineTransform3D();

	public AffineTransform3D topView() {
		return globalToScreenT_initialViewT.copy();
	}
	public AffineTransform3D sideView() {
		return rotatedView( 1, 1.0 );
	}
	public AffineTransform3D frontView() {
		return rotatedView( 0, 1.0 );
	}

	private AffineTransform3D rotatedView(int axisOfRotation, double proportionOfRotation) {
		AffineTransform3D t = globalToScreenT_initialViewT.copy();

		rotator.set( 1.0,0.0,0.0,0.0,
		             0.0,1.0,0.0,0.0,
		             0.0,0.0,1.0,0.0 );
		rotator.rotate(axisOfRotation, proportionOfRotation*0.5*Math.PI);

		// center shift
		t.set( t.get( 0, 3 ) - mouseScreenPos.getX(), 0, 3 );
		t.set( t.get( 1, 3 ) - mouseScreenPos.getY(), 1, 3 );
		t.preConcatenate( rotator );
		// center un-shift
		t.set( t.get( 0, 3 ) + mouseScreenPos.getX(), 0, 3 );
		t.set( t.get( 1, 3 ) + mouseScreenPos.getY(), 1, 3 );
		return t;
	}

	public void animateViewerToTopView(ViewerPanel panel) {
		AffineTransform3D startT = panel.state().getViewerTransform();
		AffineTransform3D endT = globalToScreenT_initialViewT.copy();
		startT.set( startT.get( 0, 3 ) - mouseScreenPos.getX(), 0, 3 );
		startT.set( startT.get( 1, 3 ) - mouseScreenPos.getY(), 1, 3 );
		endT.set( endT.get( 0, 3 ) - mouseScreenPos.getX(), 0, 3 );
		endT.set( endT.get( 1, 3 ) - mouseScreenPos.getY(), 1, 3 );
		panel.setTransformAnimator(new SimilarityTransformAnimator(
				  startT, endT, mouseScreenPos.getX(), mouseScreenPos.getY(), animationDurationMillis ));
	}
	public void animateViewerToSideView(ViewerPanel panel) {
		panel.setTransformAnimator(new AbstractTransformAnimator(animationDurationMillis) {
			@Override
			public AffineTransform3D get(double t) { return rotatedView(1,-t); }
		});
	}
	public void animateViewerToFrontView(ViewerPanel panel) {
		panel.setTransformAnimator(new AbstractTransformAnimator(animationDurationMillis) {
			@Override
			public AffineTransform3D get(double t) { return rotatedView(0,t); }
		});
	}

	public int animationDurationMillis = 1000;
}
