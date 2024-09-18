package ai.nets.samj.bdv;

import ai.nets.samj.bdv.util.SpatioTemporalView;
import ai.nets.samj.communication.model.SAMModel;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlay;
import bdv.util.BdvOverlaySource;
import bdv.util.BdvStackSource;
import bdv.viewer.ViewerPanel;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import java.util.function.Consumer;
import ai.nets.samj.bdv.polygons.Polygon3D;
import ai.nets.samj.bdv.polygons.Polygons3DExampleConsumer;

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
		this(operateOnThisImage,"SAMJ test image");
	}

	public SAMJ_BDV(final Img<T> operateOnThisImage, final String imageName) {
		this.image = operateOnThisImage;
		this.bdv = BdvFunctions.show( operateOnThisImage, imageName );
		this.viewerPanel = bdv.getBdvHandle().getViewerPanel();

		this.samjOverlay = new PromptsAndResultsDrawingOverlay();
		this.samjSource = BdvFunctions.showOverlay(samjOverlay, "SAMJ overlay", BdvOptions.options().addTo(bdv));
		samjSource.setColor(new ARGBType( this.samjOverlay.colorResults.getRGB() ));

		//register our own (polygons drawing) overlay as a polygon consumer
		this.addPolygonsConsumer(samjOverlay);
		this.addPolygonsConsumer(new Polygons3DExampleConsumer());
		installBehaviours();
	}

	private final List<Consumer<Polygon3D>> polygonConsumers = new ArrayList<>(10);
	public void addPolygonsConsumer(final Consumer<Polygon3D> consumer) {
		polygonConsumers.add(consumer);
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

	class PromptsAndResultsDrawingOverlay extends BdvOverlay implements Consumer<Polygon3D> {
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

		private List<Polygon3D> polygonList = new ArrayList<>(100);
		private boolean shouldDrawPolygons = false;

		@Override
		public void accept(Polygon3D polygon) {
			polygonList.add(polygon);
		}
		public void clearPolygons() {
			polygonList.clear();
		}

		public void setPolygons(List<Polygon3D> polygons) {
			polygonList = polygons;
		}
		public List<Polygon3D> getPolygons() {
			return polygonList;
		}

		private final BasicStroke stroke = new BasicStroke( 1.0f ); //lightweight I guess
		private Color colorPrompt = Color.GREEN;
		private Color colorResults = Color.RED;
		private int colorFromBDV = -1;

		@Override
		protected void draw(Graphics2D g) {
			if (shouldDrawLine && isLineReadyForDrawing) {
				//draws the line
				//final double uiScale = UIUtils.getUIScaleFactor( this );
				//final BasicStroke stroke = new BasicStroke( ( float ) uiScale );
				g.setStroke(stroke);
				g.setPaint(colorPrompt);
				g.drawLine(sx,sy, ex,sy);
				g.drawLine(ex,sy, ex,ey);
				g.drawLine(ex,ey, sx,ey);
				g.drawLine(sx,ey, sx,sy);
			}

			if (shouldDrawPolygons) {
				final int currentColor = samjOverlay.info.getColor().get();
				if (currentColor != colorFromBDV) {
					//NB: change color only if changed on the BDV side (mainly to prevent overuse of new())
					colorFromBDV = currentColor;
					colorResults = new Color( currentColor );
				}

				//draws the currently recognized polygons
				viewerPanel.state().getViewerTransform(pxToScreenTransform);
				g.setPaint(colorResults);
				for (Polygon3D p : polygonList) {
					for (int i = 0; i <= p.size(); i++) {
						//NB: the first (i=0) point is repeated to close the loop
						double[] coord = p.coordinate3D(i % p.size());
						if (i % 2 == 0) {
							pxToScreenTransform.apply(coord, screenCoord);
						} else {
							pxToScreenTransform.apply(coord, screenCoordB);
						}
						if (i > 0)
							//TODO: make sure the coords are not outside the screen... negative or too large, I guess
							g.drawLine((int)screenCoord[0],(int)screenCoord[1], (int)screenCoordB[0],(int)screenCoordB[1]);
					}
				}
			}
		}

		final AffineTransform3D pxToScreenTransform = new AffineTransform3D();
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

				if (!arePromptsEnabled) {
					System.out.println("Prompts disabled, click the rectangle button in the SAMJ GUI.");
					return;
				}
				if (currentlyUsedAnnotationSiteId == -1) {
					System.out.println("No annotation site is active now, create new ('A') or visit some ('W') first.");
					return;
				}
				processPrompt();
			}
		}, "samj_line", "L" );

		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			AXIS_VIEW viewDir = whatDimensionIsViewAlong( viewerPanel.state().getViewerTransform() );
			if (viewDir != AXIS_VIEW.NONE_OF_XYZ) {
				installNewAnnotationSite(viewDir, false);
			} else {
				System.out.println("Not an orthogonal view, try Shift+X, Shift+Z, or Shift+Y to get one.");
				ImageJFunctions.show( collectViewPixelData(this.image) );
			}
		}, "samj_new_original_view", "A");

		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			AXIS_VIEW viewDir = whatDimensionIsViewAlong( viewerPanel.state().getViewerTransform() );
			if (viewDir != AXIS_VIEW.NONE_OF_XYZ) {
				installNewAnnotationSite(viewDir, true);
			} else {
				System.out.println("Not an orthogonal view, try Shift+X, Shift+Z, or Shift+Y to get one.");
			}
		}, "samj_new_manipulated_view", "shift|A");

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
		Interval viewBox = annotationSitesROIs.get(currentlyUsedAnnotationSiteId);
		Interval box = new FinalInterval(
				//pattern: Math.max(viewBox.min(0),Math.min( THE_VALUE ,viewBox.max(0)))
				//to make sure the prompt is within the 'viewBox' interval
				new long[] {
					Math.max(viewBox.min(0),Math.min( Math.round(topLeftPoint.getDoublePosition(viewDir.runningAxisDim1())) ,viewBox.max(0))),
					Math.max(viewBox.min(1),Math.min( Math.round(topLeftPoint.getDoublePosition(viewDir.runningAxisDim2())) ,viewBox.max(1)))
				}, new long[] {
					Math.max(viewBox.min(0),Math.min( Math.round(bottomRightPoint.getDoublePosition(viewDir.runningAxisDim1())) ,viewBox.max(0))),
					Math.max(viewBox.min(1),Math.min( Math.round(bottomRightPoint.getDoublePosition(viewDir.runningAxisDim2())) ,viewBox.max(1)))
				} );

		System.out.println("Want to submit a box prompt: ["
				  + box.min(0) + "," + box.min(1) + " -> "
				  + box.max(0) + "," + box.max(1) + "]" );
		System.out.println("Given the current image view: "+new FinalInterval(viewBox));
		List<Polygon> polygons2D = processRectanglePrompt(box, viewBox.min(0), viewBox.min(1));

		if (!polygonConsumers.isEmpty() && !polygons2D.isEmpty()) {
			final double[] tmpVec = new double[3];
			tmpVec[viewDir.fixedAxisDim()] = topLeftPoint.getDoublePosition(viewDir.fixedAxisDim()); //NB: we can do this because of the axis-aligned views!

			final double[] matrix = new double[12];
			matrix[viewDir.runningAxisDim1()] = 1.0;
			matrix[3] = -viewBox.min(0);
			//
			matrix[4+viewDir.runningAxisDim2()] = 1.0;
			matrix[7] = -viewBox.min(1);
			//
			matrix[8+ viewDir.fixedAxisDim()] = 1.0;
			matrix[11] = -tmpVec[viewDir.fixedAxisDim()];
			final AffineTransform3D t = new AffineTransform3D();
			t.set(matrix);

			for (Polygon p : polygons2D) {
				Polygon3D.Builder builder = new Polygon3D.Builder(p.npoints, t);
				for (int i = 0; i < p.npoints; ++i) {
					tmpVec[viewDir.runningAxisDim1()] = p.xpoints[i];
					tmpVec[viewDir.runningAxisDim2()] = p.ypoints[i];
					builder.addVertex(tmpVec);
				}
				Polygon3D polygon = builder.build();
				polygonConsumers.forEach(c -> c.accept(polygon));
			}
		}

		//request redraw, just in case, after all polygons are consumed, and to make sure the prompt rectangle disappears
		viewerPanel.getDisplayComponent().repaint();
	}

	// ======================== actions - annotation sites ========================
	public void installNewAnnotationSite(final AXIS_VIEW viewDir, final boolean considerBdvRangeSetting) {
		//register the new site's data
		final int newIdx = annotationSites.size()+1;
		//
		viewerPanel.displayToGlobalCoordinates(0,0, topLeftPoint);
		double fixedDimPos = topLeftPoint.getDoublePosition( viewDir.fixedAxisDim() );
		annotationSites.put( newIdx, new AnnotationSite(
				new SpatioTemporalView(bdv.getBdvHandle()), viewDir, fixedDimPos ) );
		System.out.println("Adding a new annotation site: "+viewDir+" @ "+fixedDimPos);
		//
		List<Polygon3D> polygons = new ArrayList<>(100);
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
		annotationSitesROIs.put( newIdx, Intervals.hyperSlice(box, viewDir.fixedAxisDim()) );
		annotationSitesImages.put( newIdx, considerBdvRangeSetting ? prepareCroppedImageWithSourceSettingForSAMModel(box) : prepareCroppedImageForSAMModel(box) );
		if (showNewAnnotationSitesImages) ImageJFunctions.show( annotationSitesImages.get(newIdx), "site #"+newIdx );
	}

	public Img<T> collectViewPixelData(final Img<T> srcImg) {
		//final RealRandomAccessible<T> srcRealImg = Views.interpolate(Views.extendValue(srcImg, 0), new NearestNeighborInterpolatorFactory<>());
		final RealRandomAccessible<T> srcRealImg = Views.interpolate(Views.extendValue(srcImg, 0), new NLinearInterpolatorFactory<>());
		final RealPoint srcPos = new RealPoint(3);  //orig underlying 3D image

		final double[] viewPos = new double[2];      //the current view 2D image
		final Dimension displaySize = viewerPanel.getDisplayComponent().getSize();
		Img<T> viewImg = srcImg.factory().create(displaySize.width, displaySize.height);

		Cursor<T> viewCursor = viewImg.localizingCursor();
		while (viewCursor.hasNext()) {
			T px = viewCursor.next();
			viewCursor.localize(viewPos);
			viewerPanel.displayToGlobalCoordinates(viewPos[0],viewPos[1], srcPos); //TODO optimize (inside is a RealPoint created over and over again)
			px.setReal( srcRealImg.getAt(srcPos).getRealDouble() ); //TODO optimize (avoid using getAt())
		}

		return viewImg;
	}

	RandomAccessibleInterval<FloatType> prepareCroppedImageForSAMModel(final Interval cropOutROI) {
		//a narrow view on the source data - "spatial" aspect
		RandomAccessibleInterval<T> cropImg = Views.dropSingletonDimensions( Views.interval(image, cropOutROI) );

		//"intensity" aspect
		final double[] valExtremes = new double[] {Double.MAX_VALUE, Double.MIN_VALUE};
		LoopBuilder.setImages(cropImg).forEachPixel(p -> {
			double val = p.getRealDouble();
			valExtremes[0] = Math.min(valExtremes[0], val);
			valExtremes[1] = Math.max(valExtremes[1], val);
		});
		System.out.println("Massaging discovered min = "+valExtremes[0]+" and max = "+valExtremes[1]);
		if (valExtremes[1] == valExtremes[0]) valExtremes[1] += 1.0;

		//massage both aspects into an outcome image
		final double range = valExtremes[1] - valExtremes[0];
		final Img<FloatType> explicitCroppedFloatImg = ArrayImgs.floats(cropImg.max(0)-cropImg.min(0)+1, cropImg.max(1)-cropImg.min(1)+1);
		LoopBuilder.setImages(cropImg, explicitCroppedFloatImg).forEachPixel( (i, o) -> o.setReal((i.getRealDouble() - valExtremes[0]) / range) );
		return explicitCroppedFloatImg;
	}

	RandomAccessibleInterval<FloatType> prepareCroppedImageWithSourceSettingForSAMModel(final Interval cropOutROI) {
		//a narrow view on the source data - "spatial" aspect
		RandomAccessibleInterval<T> cropImg = Views.dropSingletonDimensions( Views.interval(image, cropOutROI) );

		//"intensity" aspect
		final double min = ((BdvStackSource<T>)bdv).getConverterSetups().get(0).getDisplayRangeMin();
		double max = ((BdvStackSource<T>)bdv).getConverterSetups().get(0).getDisplayRangeMax();
		System.out.println("Massaging is taking min = "+min+" and max = "+max);
		if (max == min) max += 1.0;

		//massage both aspects into an outcome image
		final double range = max - min;
		final Img<FloatType> explicitCroppedFloatImg = ArrayImgs.floats(cropImg.max(0)-cropImg.min(0)+1, cropImg.max(1)-cropImg.min(1)+1);
		LoopBuilder.setImages(cropImg, explicitCroppedFloatImg).forEachPixel( (i, o) -> o.setReal(
				Math.min( Math.max(i.getRealDouble() - min, 0.0) / range, 1.0 ) ) );
		return explicitCroppedFloatImg;
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
		//samjOverlay.stopDrawing();
	}

	//maps internal ID of a view (which was registered with the key to start SAMJ Annotation) to
	//an object that represents that exact view, and another map for polygons associated with that view
	private final Map<Integer, AnnotationSite> annotationSites = new HashMap<>(100);
	private final Map<Integer, List<Polygon3D>> annotationSitesPolygons = new HashMap<>(100);
	private final Map<Integer, RandomAccessibleInterval<FloatType>> annotationSitesImages = new HashMap<>(100);
	private final Map<Integer, Interval> annotationSitesROIs = new HashMap<>(100);
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
		List<Polygon> export = new ArrayList<>();
		annotationSitesPolygons.getOrDefault(siteId, Collections.emptyList()).forEach(p -> {
			Polygon awtP = new Polygon();
			for (int i = 0; i < p.size(); ++i) {
				double[] c = p.coordinate2D(i);
				awtP.addPoint((int)Math.round(c[0]), (int)Math.round(c[1]));
			}
			export.add(awtP);
		});
		return export;
	}

	public RandomAccessibleInterval<FloatType> getImageFromTheLastUsedAnnotationSite() {
		return getImageFromAnnotationSite(lastVisitedAnnotationSiteId);
	}
	public RandomAccessibleInterval<FloatType> getImageFromTheCurrentAnnotationSite() {
		return getImageFromAnnotationSite(currentlyUsedAnnotationSiteId);
	}
	public RandomAccessibleInterval<FloatType> getImageFromAnnotationSite(int siteId) {
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

	protected List<Polygon> processRectanglePrompt(final Interval boxInGlobalPxCoords,
	                                               final long xOffset, final long yOffset) {
		if (fakeResults) return processRectanglePromptFake(boxInGlobalPxCoords);

		Interval boxInLocalPx = new FinalInterval(
				new long[] {
					boxInGlobalPxCoords.min(0) -xOffset,
					boxInGlobalPxCoords.min(1) -yOffset
				}, new long[] {
					boxInGlobalPxCoords.max(0) -xOffset,
					boxInGlobalPxCoords.max(1) -yOffset
				} );
		try {
			List<Polygon> polygons = activeNN.fetch2dSegmentation(boxInLocalPx);
			polygons.forEach(p -> {
				for (int i = 0; i < p.npoints; ++i) {
					p.xpoints[i] += xOffset;
					p.ypoints[i] += yOffset;
				}
			});
			return polygons; //polygons in global px coords again
		} catch (IOException | RuntimeException | InterruptedException e) {
			System.out.println("BTW, an error working with the SAM: "+e.getMessage());
		}
		return Collections.emptyList();
	}

	protected List<Polygon> processRectanglePromptFake(final Interval boxInGlobalPxCoords) {
		List<Polygon> fakes = new ArrayList<>(2);
		fakes.add( createFakePolygon(boxInGlobalPxCoords) );
		return fakes;
	}

	protected Polygon createFakePolygon(final Interval insideThisBox) {
		Random rand = new Random();
		final int BOUND = 8;

		int minx = (int)insideThisBox.min(0), maxx = (int)insideThisBox.max(0);
		int miny = (int)insideThisBox.min(1), maxy = (int)insideThisBox.max(1);

		Polygon p = new Polygon();
		int r = rand.nextInt(BOUND);
		addPointsAlongLine(p, minx, miny, (minx+maxx)/2, miny+r);
		addPointsAlongLine(p, (minx+maxx)/2, miny+r, maxx, miny);

		r = rand.nextInt(BOUND);
		addPointsAlongLine(p, maxx, miny, maxx-r, (miny+maxy)/2);
		addPointsAlongLine(p, maxx-r, (miny+maxy)/2, maxx, maxy);

		r = rand.nextInt(BOUND);
		addPointsAlongLine(p, maxx, maxy, (minx+maxx)/2, maxy-r);
		addPointsAlongLine(p, (minx+maxx)/2, maxy-r, minx, maxy);

		r = rand.nextInt(BOUND);
		addPointsAlongLine(p, minx, maxy, minx+r, (miny+maxy)/2);
		addPointsAlongLine(p, minx+r, (miny+maxy)/2, minx, miny);
		return p;
	}

	protected void addPointsAlongLine(final Polygon p, int sx, int sy, int ex, int ey) {
		float steps = Math.max(Math.abs(ex-sx), Math.abs(ey-sy));
		for (int i = 0; i < steps; ++i) {
			int x = (int)( (float)i * (float)(ex-sx)/steps );
			int y = (int)( (float)i * (float)(ey-sy)/steps );
			p.addPoint(sx+x,sy+y);
		}
	}
}
