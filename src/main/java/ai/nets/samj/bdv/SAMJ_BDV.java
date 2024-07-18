package ai.nets.samj.bdv;

import ai.nets.samj.bdv.util.SpatioTemporalView;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlay;
import bdv.util.BdvOverlaySource;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealPoint;
import net.imglib2.img.Img;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
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
import java.util.List;
import java.util.ArrayList;

public class SAMJ_BDV<T extends RealType<T> & NativeType<T>> {
	public SAMJ_BDV(final Img<T> operateOnThisImage) {
		this.image = operateOnThisImage;
		this.bdv = BdvFunctions.show( operateOnThisImage, "SAMJ test image" );
		this.viewerPanel = bdv.getBdvHandle().getViewerPanel();

		this.samjOverlay = new PromptsAndResultsDrawingOverlay();
		this.samjSource = BdvFunctions.showOverlay(samjOverlay, "SAMJ overlay", BdvOptions.options().addTo(bdv));
		samjSource.setColor(new ARGBType( this.samjOverlay.colorResults.getRGB() ));

		installBehaviours();
	}

	private final Img<T> image;
	private final Bdv bdv;
	final ViewerPanel viewerPanel;

	// ======================== overlay content ========================
	final PromptsAndResultsDrawingOverlay samjOverlay;
	final BdvOverlaySource<BdvOverlay> samjSource;

	class PromptsAndResultsDrawingOverlay extends BdvOverlay {
		private int sx,sy; //starting coordinate of the line, the "first end"
		private int ex,ey; //ending coordinate of the line, the "second end"
		private boolean shouldDrawLine = false;
		private boolean isLineReadyForDrawing = false;

		public void setStartOfLine(int x, int y) {
			sx = x;
			sy = y;
			isLineReadyForDrawing = false;
		}
		public void setEndOfLine(int x, int y) {
			ex = x;
			ey = y;
			isLineReadyForDrawing = true;
		}

		public void stopDrawing() {
			shouldDrawLine = false;
			shouldDrawPolygons = false;
		}
		public void startDrawing() {
			isLineReadyForDrawing = false;
			shouldDrawLine = true;
			shouldDrawPolygons = true;
		}

		private List<Polygon> polygonList = new ArrayList<>(100);
		private boolean shouldDrawPolygons = false;

		public void addPolygon(final Polygon p) {
			polygonList.add( new Polygon(p.xpoints,p.ypoints,p.npoints) );
		}
		public void clearPolygons() {
			polygonList.clear();
		}

		public void setPolygons(List<Polygon> polygons) {
			polygonList = polygons;
		}
		public List<Polygon> getPolygons() {
			return polygonList;
		}

		private final BasicStroke stroke = new BasicStroke( 1.0f ); //lightweight I guess
		private Color colorPrompt = Color.GREEN;
		private Color colorResults = Color.RED;

		@Override
		protected void draw(Graphics2D g) {
			if (shouldDrawLine && isLineReadyForDrawing) {
				//draws the line
				//final double uiScale = UIUtils.getUIScaleFactor( this );
				//final BasicStroke stroke = new BasicStroke( ( float ) uiScale );
				g.setStroke(stroke);
				g.setPaint(colorPrompt);
				g.drawLine(sx,sy, ex,ey);
			}

			if (shouldDrawPolygons) {
				//draws the currently recognized polygons
				viewerPanel.state().getViewerTransform(pxToScreenTransform);
				g.setPaint(colorResults);
				for (Polygon p : polygonList) {
					for (int i = 0; i < p.xpoints.length; i++) {
						//pixel coordinate
						pxCoord[0] = p.xpoints[i];
						pxCoord[1] = p.ypoints[i];
						if (i % 2 == 0) {
							pxToScreenTransform.apply(pxCoord, screenCoord);
						} else {
							pxToScreenTransform.apply(pxCoord, screenCoordB);
						}
						//TODO: will be missing the line between the first and the last point
						if (i > 0)
							g.drawLine((int)screenCoord[0],(int)screenCoord[1], (int)screenCoordB[0],(int)screenCoordB[1]);
					}
				}
			}
		}

		final AffineTransform3D pxToScreenTransform = new AffineTransform3D();
		final float[] pxCoord = new float[3];
		final float[] screenCoord = new float[3];
		final float[] screenCoordB = new float[3];
	}

	// ======================== actions - behaviours ========================
	void installBehaviours() {
		//"loose" the annotation site as soon as the BDV's viewport is changed
		bdv.getBdvHandle().getViewerPanel().renderTransformListeners().add( someNewIgnoredTransform -> {
				if (!ignoreNextTransformEvent) lostViewOfAnnotationSite();
				ignoreNextTransformEvent = false;
			} );

		final Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bdv.getBdvHandle().getTriggerbindings(), "samj" );

		//install behaviour for moving a line in the BDV view, with shortcut "L"
		behaviours.behaviour( new DragBehaviour() {
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
				System.out.println("Adding a new annotation site");
				installNewAnnotationSite();
			}
		}, "samj_new_view", "A");

		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			if (annotationSites.isEmpty()) {
				System.out.println("Switching NOT... no annotation sites available yet.");
				return;
			}
			int nextSiteId = lastVisitedAnnotationSiteId + 1;
			if (nextSiteId > annotationSites.size()) nextSiteId = 1;
			System.out.println("Switching to annotation site: "+nextSiteId);
			displayAnnotationSite(nextSiteId);
		}, "samj_next_view", "W");

		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			if (annotationSites.isEmpty()) {
				System.out.println("Switching NOT... no annotation sites available yet.");
				return;
			}
			System.out.println("Switching to last visited annotation site: "+lastVisitedAnnotationSiteId);
			displayAnnotationSite(lastVisitedAnnotationSiteId);
		}, "samj_last_view", "shift|W");
	}

	// ======================== actions - annotation sites ========================
	public void installNewAnnotationSite() {
		//register the new site's data
		final int newIdx = annotationSites.size()+1;
		List<Polygon> polygons = new ArrayList<>(100);
		annotationSites.put( newIdx, new SpatioTemporalView(bdv.getBdvHandle()) );
		annotationSitesPolygons.put( newIdx, polygons );
		currentlyUsedAnnotationSiteId = newIdx;
		lastVisitedAnnotationSiteId = newIdx;

		//make it visible
		samjOverlay.setPolygons(polygons);
		samjOverlay.startDrawing();

		//prepare pixel data, get SAMJ ready, update the combobox
		// ---------- pixel data ----------
		final RealPoint topLeftROI = new RealPoint(3);
		final RealPoint bottomRightROI = new RealPoint(3);
		final Dimension displaySize = viewerPanel.getDisplayComponent().getSize();
		viewerPanel.displayToGlobalCoordinates(0,0, topLeftROI);
		viewerPanel.displayToGlobalCoordinates(displaySize.width,displaySize.height, bottomRightROI);
		System.out.println("image ROI: "+topLeftROI+" -> "+bottomRightROI);
		//no scaling of the image! just extract the view
		//TODO
		//Interval roi = new FinalInterval(topLeftROI,bottomRightROI);

		//TODO: fixed here z-img-coord
		samjOverlay.pxCoord[2] = topLeftROI.getFloatPosition(2);
	}

	/**
	 * @param id ID of the requested annotation site.
	 * @return False if the requested site is not available, and thus no action was taken.
	 */
	public boolean displayAnnotationSite(int id) {
		if (!annotationSites.containsKey(id)) return false;

		ignoreNextTransformEvent = true;
		annotationSites.get(id).applyOnThis(bdv.getBdvHandle());
		//rename source -- BDV ain't supporting this easily
		currentlyUsedAnnotationSiteId = id;
		lastVisitedAnnotationSiteId = id;

		samjOverlay.setPolygons(annotationSitesPolygons.get(id));
		samjOverlay.startDrawing();
		return true;
	}

	private boolean ignoreNextTransformEvent = false;

	private void lostViewOfAnnotationSite() {
		//rename source -- BDV ain't supporting this easily
		//store polygons from the last annotation -- they're shared, so no explicit action is needed
		currentlyUsedAnnotationSiteId = -1;
		//
		//disable drawing of lines and polygons
		samjOverlay.stopDrawing();
	}

	//maps internal ID of a view (which was registered with the key to start SAMJ Annotation) to
	//an object that represents that exact view, and another map for polygons associated with that view
	private final Map<Integer, SpatioTemporalView> annotationSites = new HashMap<>(100);
	private final Map<Integer, List<Polygon>> annotationSitesPolygons = new HashMap<>(100);
	private int currentlyUsedAnnotationSiteId = -1;
	private int lastVisitedAnnotationSiteId = -1;

	public Collection<Integer> getAnnotationSitesIDs() {
		return Collections.unmodifiableCollection( annotationSites.keySet() );
	}

	// ======================== AXIS_VIEW stuff ========================
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
