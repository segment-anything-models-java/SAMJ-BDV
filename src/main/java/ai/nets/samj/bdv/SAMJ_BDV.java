package ai.nets.samj.bdv;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlay;
import bdv.util.BdvOverlaySource;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealPositionable;
import net.imglib2.RealPoint;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.awt.*;

public class SAMJ_BDV<T extends RealType<T> & NativeType<T>> {
	public SAMJ_BDV(final Img<T> operateOnThisImage) {
		this.image = operateOnThisImage;
		this.bdv = BdvFunctions.show( operateOnThisImage, "SAMJ test image" );
		this.viewerPanel = bdv.getBdvHandle().getViewerPanel();

		this.samjOverlay = new PromptsAndResultsDrawingOverlay();
		this.samjSource = BdvFunctions.showOverlay(samjOverlay, "SAMJ overlay", BdvOptions.options().addTo(bdv));

		installBehaviours();
	}

	private final Img<T> image;
	private final Bdv bdv;
	final ViewerPanel viewerPanel;

	final PromptsAndResultsDrawingOverlay samjOverlay;
	final BdvOverlaySource<BdvOverlay> samjSource;

	class PromptsAndResultsDrawingOverlay extends BdvOverlay {
		public void setStartOfLine(int x, int y) {
			sx = x;
			sy = y;
			canBeDisplayed = false;
		}

		public void setEndOfLine(int x, int y) {
			ex = x;
			ey = y;
			canBeDisplayed = true;
		}

		public void stopDrawingLine() {
			canBeDisplayed = false;
		}

		private int sx,sy; //starting coordinate of the line, the "first end"
		private int ex,ey; //ending coordinate of the line, the "second end"
		private boolean canBeDisplayed = false;

		private final BasicStroke stroke = new BasicStroke( 1.0f ); //lightweight I guess

		@Override
		protected void draw(Graphics2D g) {
			if (canBeDisplayed) {
				//final double uiScale = UIUtils.getUIScaleFactor( this );
				//final BasicStroke stroke = new BasicStroke( ( float ) uiScale );
				g.setStroke( stroke );
				g.setPaint( Color.GREEN );
				g.drawLine(sx,sy, ex,ey);
			}
		}
	}

	void installBehaviours() {
		//stops drawing the line as soon as viewport has changed
		//or, TODO, adjust the coordinates based on the new transform (but that's not what we want for SAMJ)
		bdv.getBdvHandle().getViewerPanel().renderTransformListeners().add(transform -> samjOverlay.stopDrawingLine());

		// Install behaviour for moving a line into img with shortcut "L"
		final Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bdv.getBdvHandle().getTriggerbindings(), "samj" );
		behaviours.behaviour( new DragBehaviour()
		{
			@Override
			public void init( final int x, final int y )
			{
				samjOverlay.setStartOfLine(x,y);
			}

			@Override
			public void drag( final int x, final int y )
			{
				samjOverlay.setEndOfLine(x,y);
			}

			@Override
			public void end( final int x, final int y )
			{
				samjOverlay.setEndOfLine(x,y);
				System.out.println("TRIGGER AFTER A LINE WAS INSERTED");
			}
		}, "samj_line", "L" );


		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			AXIS_VIEW viewDir = whatDimensionIsViewAlong( viewerPanel.state().getViewerTransform() );
			System.out.println("View: "+viewDir);

			//if (viewDir != AXIS_VIEW.NONE_OF_XYZ) {
			if (viewDir == AXIS_VIEW.ALONG_Z) {
				System.out.println("Going to start a SAMJ here");
				final RealPoint topLeftROI = new RealPoint(3);
				final RealPositionable bottomRightROI = new RealPoint(3);
				final Dimension displaySize = viewerPanel.getDisplayComponent().getSize();
				viewerPanel.displayToGlobalCoordinates(0,0, topLeftROI);
				viewerPanel.displayToGlobalCoordinates(displaySize.width,displaySize.height, bottomRightROI);
				System.out.println("image ROI: "+topLeftROI+" -> "+bottomRightROI);

				//TODO: fixed here z-img-coord
				samjOverlay.pxCoord[2] = topLeftROI.getFloatPosition(2);
			}
		}, "samj_new_view", "A");
	}

	public enum AXIS_VIEW {
		ALONG_X,
		ALONG_Y,
		ALONG_Z,
		NONE_OF_XYZ
	}

	/**
	 * @param view
	 * @return -1 if the view is not along any axis, or 0,1,2 for x-,y-,z-axis
	 */
	public static AXIS_VIEW whatDimensionIsViewAlong(final AffineTransform3D view) {
		/* along x: 0,0,?    along y: ?,0,0    along z: ?,0,0
		 *          0,?,0             0,0,?             0,?,0
		 *          ?,0,0             0,?,0             0,0,?
		 */
		if ( isZeroElem(view, 0,0) ) {
			//candidate for "along x"
			if (isZeroElem(view, 0,1)
					&& isZeroElem(view, 1,0) && isZeroElem(view, 1,2)
					&& isZeroElem(view, 2,1) && isZeroElem(view, 2,2))
					return AXIS_VIEW.ALONG_X;
		} else if (isZeroElem(view, 0,1) && isZeroElem(view, 0,2)) {
			//candidate for "along y or z"
			if (isZeroElem(view, 1,1)) {
				//for "along y"
				if (isZeroElem(view, 1,0)
						&& isZeroElem(view, 2,0) && isZeroElem(view, 2,2))
						return AXIS_VIEW.ALONG_Y;
			} else {
				//for "along z"
				if (isZeroElem(view, 1,0) && isZeroElem(view, 1,2)
						&& isZeroElem(view, 2,0) && isZeroElem(view, 2,1))
						return AXIS_VIEW.ALONG_Z;
			}
		}
		return AXIS_VIEW.NONE_OF_XYZ;
	}

	public static boolean isZeroElem(final AffineTransform3D view, int row, int column) {
		return isZero( view.get(row,column) );
	}

	public static boolean isZero(double val) {
		final double EPSILON = 0.00001;
		return -EPSILON < val && val < EPSILON;
	}
}
