package ai.nets.samj.bdv;

import ai.nets.samj.bdv.util.SpatioTemporalView;
import ai.nets.samj.communication.model.SAMModel;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlay;
import bdv.util.BdvOverlaySource;
import bdv.viewer.ViewerPanel;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.view.Views;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.awt.*;
import java.util.Random;

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

	boolean showNewAnnotationSitesImages = false;
	boolean fakeResults = false;

	public void showMessage(final String msg) {
		if (msg != null) bdv.getBdvHandle().getViewerPanel().showMessage(msg);
	}

	public void close() {
		bdv.getBdvHandle().close();
	}

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

		/** Possibly switch line ends coordinates such that line would go
		 * from "top"-"left", i.e., from smaller to larger coordinates. */
		public void normalizeLineEnds() {
			int tmp;
			if (sx > ex) { tmp = sx; sx = ex; ex = tmp; }
			if (sy > ey) { tmp = sy; sy = ey; ey = tmp; }
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
		public void addPolygon(final Polygon p, final int xOffset, final int yOffset) {
			Polygon shiftedP = new Polygon();
			for (int i = 0; i < p.xpoints.length; ++i) {
				shiftedP.addPoint(p.xpoints[i]+xOffset, p.ypoints[i]+yOffset);
			}
			polygonList.add(shiftedP);
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
				AXIS_VIEW viewDir = annotationSites.get(currentlyUsedAnnotationSiteId).viewDir;
				pxCoord[viewDir.fixedAxisDim()] = annotationSites.get(currentlyUsedAnnotationSiteId).fixedDimPos;
				viewerPanel.state().getViewerTransform(pxToScreenTransform);
				g.setPaint(colorResults);
				for (Polygon p : polygonList) {
					for (int i = 0; i <= p.xpoints.length; i++) {
						//NB: the first (i=0) point is repeated to close the loop
						pxCoord[viewDir.runningAxisDim1()] = p.xpoints[i % p.xpoints.length];
						pxCoord[viewDir.runningAxisDim2()] = p.ypoints[i % p.xpoints.length];
						if (i % 2 == 0) {
							pxToScreenTransform.apply(pxCoord, screenCoord);
						} else {
							pxToScreenTransform.apply(pxCoord, screenCoordB);
						}
						if (i > 0)
							//TODO: make sure the coords are not outside the screen... negative or too large, I guess
							g.drawLine((int)screenCoord[0],(int)screenCoord[1], (int)screenCoordB[0],(int)screenCoordB[1]);
					}
				}
			}
		}

		final AffineTransform3D pxToScreenTransform = new AffineTransform3D();
		final double[] pxCoord = new double[3];
		final double[] screenCoord = new double[3];
		final double[] screenCoordB = new double[3];
	}

	// ======================== actions - behaviours ========================
	void installBehaviours() {
		//"loose" the annotation site as soon as the BDV's viewport is changed
		bdv.getBdvHandle().getViewerPanel().transformListeners().add( someNewIgnoredTransform -> {
				//System.out.println("render transform event, ignore="+ignoreNextTransformEvent);
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
				samjOverlay.isLineReadyForDrawing = false;

				if (arePromptsEnabled && currentlyUsedAnnotationSiteId > -1) processPrompt();
			}
		}, "samj_line", "L" );

		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			AXIS_VIEW viewDir = whatDimensionIsViewAlong( viewerPanel.state().getViewerTransform() );
			if (viewDir != AXIS_VIEW.NONE_OF_XYZ) installNewAnnotationSite(viewDir);
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

	// ======================== actions - prompts ========================
	private boolean arePromptsEnabled = true;
	public void enablePrompts() { arePromptsEnabled = true; }
	public void disablePrompts() { arePromptsEnabled = false; }

	private final RealPoint topLeftPoint = new RealPoint(3);
	private final RealPoint bottomRightPoint = new RealPoint(3);
	//
	private void processPrompt() {
		samjOverlay.normalizeLineEnds();
		viewerPanel.displayToGlobalCoordinates(samjOverlay.sx,samjOverlay.sy, topLeftPoint);
		viewerPanel.displayToGlobalCoordinates(samjOverlay.ex,samjOverlay.ey, bottomRightPoint);
		AXIS_VIEW viewDir = annotationSites.get(currentlyUsedAnnotationSiteId).viewDir;
		Interval viewImg = annotationSitesImages.get(currentlyUsedAnnotationSiteId);
		Interval box = new FinalInterval(
				//pattern: Math.max(viewImg.min(0),Math.min( THE_VALUE ,viewImg.max(0)))
				//to make sure the prompt is within the 'viewImg' interval
				new long[] {
					Math.max(viewImg.min(0),Math.min( Math.round(topLeftPoint.getDoublePosition(viewDir.runningAxisDim1())) ,viewImg.max(0))),
					Math.max(viewImg.min(1),Math.min( Math.round(topLeftPoint.getDoublePosition(viewDir.runningAxisDim2())) ,viewImg.max(1)))
				}, new long[] {
					Math.max(viewImg.min(0),Math.min( Math.round(bottomRightPoint.getDoublePosition(viewDir.runningAxisDim1())) ,viewImg.max(0))),
					Math.max(viewImg.min(1),Math.min( Math.round(bottomRightPoint.getDoublePosition(viewDir.runningAxisDim2())) ,viewImg.max(1)))
				} );
		System.out.println("Want to submit a box prompt: ["
				  + box.min(0) + "," + box.min(1) + " -> "
				  + box.max(0) + "," + box.max(1) + "]" );
		System.out.println("Given the current image view: "+new FinalInterval(viewImg));
		if (fakeResults) {
			processRectanglePromptFake(box);
		} else {
			processRectanglePrompt(box, viewImg.min(0),viewImg.min(1));
		}
		viewerPanel.getDisplayComponent().repaint();
	}

	// ======================== actions - annotation sites ========================
	public void installNewAnnotationSite(final AXIS_VIEW viewDir) {
		//register the new site's data
		final int newIdx = annotationSites.size()+1;
		//
		viewerPanel.displayToGlobalCoordinates(0,0, topLeftPoint);
		double fixedDimPos = topLeftPoint.getDoublePosition( viewDir.fixedAxisDim() );
		annotationSites.put( newIdx, new AnnotationSite(
				new SpatioTemporalView(bdv.getBdvHandle()), viewDir, fixedDimPos ) );
		System.out.println("Adding a new annotation site: "+viewDir+" @ "+fixedDimPos);
		//
		List<Polygon> polygons = new ArrayList<>(100);
		annotationSitesPolygons.put( newIdx, polygons );
		currentlyUsedAnnotationSiteId = newIdx;
		lastVisitedAnnotationSiteId = newIdx;

		//make it visible
		samjOverlay.setPolygons(polygons);
		samjOverlay.startDrawing();

		// ---------- pixel data ----------
		//prepare pixel data, get SAMJ ready, update the combobox
		//no scaling of the image! just extract the view
		final Dimension displaySize = viewerPanel.getDisplayComponent().getSize();
		viewerPanel.displayToGlobalCoordinates(0,0, topLeftPoint);
		viewerPanel.displayToGlobalCoordinates(displaySize.width,displaySize.height, bottomRightPoint);
		//BDV's full-view corresponds to pixel coords at diagonal topLeftPoint -> bottomRightPoint,
		//intersect it with the 'image' to be sure to stay within the bounds
		Interval box = new FinalInterval(
				new long[] {
					Math.max(Math.round(topLeftPoint.getDoublePosition(0)), image.min(0)),
					Math.max(Math.round(topLeftPoint.getDoublePosition(1)), image.min(1)),
					Math.max(Math.round(topLeftPoint.getDoublePosition(2)), image.min(2))
				}, new long[] {
					Math.min(Math.round(bottomRightPoint.getDoublePosition(0)), image.max(0)),
					Math.min(Math.round(bottomRightPoint.getDoublePosition(1)), image.max(1)),
					Math.min(Math.round(bottomRightPoint.getDoublePosition(2)), image.max(2))
				} );
		System.out.println("image ROI: "+topLeftPoint+" -> "+bottomRightPoint);
		System.out.println("image ROI: "+box);
		annotationSitesImages.put( newIdx, Views.dropSingletonDimensions(Views.interval(image, box)) );
		if (showNewAnnotationSitesImages) ImageJFunctions.show( annotationSitesImages.get(newIdx), "site #"+newIdx );
	}

	/**
	 * @param id ID of the requested annotation site.
	 * @return False if the requested site is not available, and thus no action was taken.
	 */
	public boolean displayAnnotationSite(int id) {
		if (!annotationSites.containsKey(id)) return false;

		ignoreNextTransformEvent = true;
		annotationSites.get(id).view.applyOnThis(bdv.getBdvHandle());
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
	private final Map<Integer, AnnotationSite> annotationSites = new HashMap<>(100);
	private final Map<Integer, List<Polygon>> annotationSitesPolygons = new HashMap<>(100);
	private final Map<Integer, RandomAccessibleInterval<T>> annotationSitesImages = new HashMap<>(100);
	private int currentlyUsedAnnotationSiteId = -1;
	private int lastVisitedAnnotationSiteId = -1;

	public Collection<Integer> getAnnotationSitesIDs() {
		return Collections.unmodifiableCollection( annotationSites.keySet() );
	}

	public static class AnnotationSite {
		AnnotationSite(SpatioTemporalView view, AXIS_VIEW viewDir, double fixedDimPos) {
			this.view = view;
			this.viewDir = viewDir;
			this.fixedDimPos = fixedDimPos;
		}
		SpatioTemporalView view;
		AXIS_VIEW viewDir;
		double fixedDimPos;
	}

	// ======================== data - annotation sites ========================
	public List<Polygon> getPolygonsFromTheLastUsedAnnotationSite() {
		return getPolygonsFromAnnotationSite(lastVisitedAnnotationSiteId);
	}
	public List<Polygon> getPolygonsFromTheCurrentAnnotationSite() {
		return getPolygonsFromAnnotationSite(currentlyUsedAnnotationSiteId);
	}
	public List<Polygon> getPolygonsFromAnnotationSite(int siteId) {
		return annotationSitesPolygons.getOrDefault(siteId, Collections.emptyList());
	}

	public RandomAccessibleInterval<T> getImageFromTheLastUsedAnnotationSite() {
		return getImageFromAnnotationSite(lastVisitedAnnotationSiteId);
	}
	public RandomAccessibleInterval<T> getImageFromTheCurrentAnnotationSite() {
		return getImageFromAnnotationSite(currentlyUsedAnnotationSiteId);
	}
	public RandomAccessibleInterval<T> getImageFromAnnotationSite(int siteId) {
		return annotationSitesImages.getOrDefault(siteId, null);
	}

	// ======================== AXIS_VIEW stuff ========================
	public enum AXIS_VIEW {
		ALONG_X,
		ALONG_Y,
		ALONG_Z,
		NONE_OF_XYZ;

		private static final int[] RUNNING_DIM1 = new int[] {1,0,0,0};
		private static final int[] RUNNING_DIM2 = new int[] {2,2,1,0};
		private static final int[] FIXED_DIM =    new int[] {0,1,2,0};
		public int runningAxisDim1() {
			return RUNNING_DIM1[this.ordinal()];
		}
		public int runningAxisDim2() {
			return RUNNING_DIM2[this.ordinal()];
		}
		public int fixedAxisDim() {
			return FIXED_DIM[this.ordinal()];
		}
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

	// ======================== SAM network interaction ========================
	private SAMModel activeNN = null; //NN = neural network
	private int activeContextInNetwork = -1;

	public void startUsingThisSAMModel(SAMModel network) {
		this.activeNN = network;
	}
	public void stopCommunicatingToSAMModel() {
		this.activeNN = null;
	}

	protected void askSAMModelToSwitchContext(int siteId) {
		if (activeNN == null) return;
		if (activeContextInNetwork == siteId) return;
		//
		activeContextInNetwork = siteId;
		System.out.println("SWITCHING NETWORK CONTEXT to id: "+siteId);
	}

	protected void processRectanglePrompt(final Interval boxInGlobalPxCoords,
	                                      final long xOffset, final long yOffset) {
		Interval boxInLocalPx = new FinalInterval(
				new long[] {
					boxInGlobalPxCoords.min(0) -xOffset,
					boxInGlobalPxCoords.min(1) -yOffset
				}, new long[] {
					boxInGlobalPxCoords.max(0) -xOffset,
					boxInGlobalPxCoords.max(1) -yOffset
				} );
		try {
			activeNN
					.fetch2dSegmentation(boxInLocalPx)
					.forEach(polygon -> samjOverlay.addPolygon(polygon, (int)xOffset,(int)yOffset));
		} catch (IOException | RuntimeException | InterruptedException e) {
			System.out.println("BTW, an error working with the SAM: "+e.getMessage());
		}
	}

	protected void processRectanglePromptFake(final Interval boxInGlobalPxCoords) {
		samjOverlay.addPolygon( createFakePolygon(boxInGlobalPxCoords) );
	}

	protected Polygon createFakePolygon(final Interval insideThisBox) {
		Random rand = new Random();
		final int BOUND = 4;

		int minx = (int)insideThisBox.min(0), maxx = (int)insideThisBox.max(0);
		int miny = (int)insideThisBox.min(1), maxy = (int)insideThisBox.max(1);

		Polygon p = new Polygon();
		p.addPoint(minx,          miny);
		p.addPoint((minx+maxx)/2, miny+rand.nextInt(BOUND));
		p.addPoint(maxx,          miny);

		p.addPoint(maxx-rand.nextInt(BOUND), (miny+maxy)/2);

		p.addPoint(maxx,          maxy);
		p.addPoint((minx+maxx)/2, maxy-rand.nextInt(BOUND));
		p.addPoint(minx,          maxy);

		p.addPoint(minx+rand.nextInt(BOUND), (miny+maxy)/2);
		return p;
	}
}
