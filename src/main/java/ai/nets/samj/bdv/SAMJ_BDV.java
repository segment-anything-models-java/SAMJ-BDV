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
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
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
import ai.nets.samj.bdv.polygons.PlanarPolygonIn3D;
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
		samjSource.setColor(new ARGBType( this.samjOverlay.colorPolygons.getRGB() ));

		//register our own (polygons drawing) overlay as a polygon consumer
		this.addPolygonsConsumer(samjOverlay);
		this.addPolygonsConsumer(new Polygons3DExampleConsumer());
		installBehaviours();
	}

	private final List<Consumer<PlanarPolygonIn3D>> polygonConsumers = new ArrayList<>(10);
	public void addPolygonsConsumer(final Consumer<PlanarPolygonIn3D> consumer) {
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

	class PromptsAndResultsDrawingOverlay extends BdvOverlay implements Consumer<PlanarPolygonIn3D> {
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

		private List<PlanarPolygonIn3D> polygonList = new ArrayList<>(500);
		private boolean shouldDrawPolygons = false;

		@Override
		public void accept(PlanarPolygonIn3D polygon) {
			polygonList.add(polygon);
		}

		public void setPolygons(List<PlanarPolygonIn3D> polygons) {
			polygonList = polygons;
		}
		public Collection<PlanarPolygonIn3D> getPolygons() {
			return polygonList;
		}
		public void clearPolygons() {
			polygonList.clear();
		}

		private final BasicStroke stroke = new BasicStroke( 1.0f ); //lightweight I guess
		private Color colorPrompt = Color.GREEN;
		private Color colorPolygons = Color.RED;
		private int colorFromBDV = -1;

		double toleratedOffViewPlaneDistance = 6.0;

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
					colorPolygons = new Color( currentColor );
				}

				//draws the currently recognized polygons
				viewerPanel.state().getViewerTransform(imgToScreenTransform);
				g.setPaint(colorPolygons);
				boolean isCloseToViewingPlane = true, isCloseToViewingPlaneB = true;
				for (PlanarPolygonIn3D p : polygonList) {
					p.getTransformTo3d(polyToImgTransform);
					polyToImgTransform.preConcatenate(imgToScreenTransform);
					for (int i = 0; i <= p.size(); i++) {
						//NB: the first (i=0) point is repeated to close the loop
						p.coordinate2D(i % p.size(), auxCoord3D);
						if (i % 2 == 0) {
							polyToImgTransform.apply(auxCoord3D, screenCoord);
							isCloseToViewingPlane = Math.abs(screenCoord[2]) < toleratedOffViewPlaneDistance;
						} else {
							polyToImgTransform.apply(auxCoord3D, screenCoordB);
							isCloseToViewingPlaneB = Math.abs(screenCoordB[2]) < toleratedOffViewPlaneDistance;
						}
						if (i > 0 && isCloseToViewingPlane && isCloseToViewingPlaneB)
							//TODO: make sure the coords are not outside the screen... negative or too large, I guess
							g.drawLine((int)screenCoord[0],(int)screenCoord[1], (int)screenCoordB[0],(int)screenCoordB[1]);
					}
				}
			}
		}

		final AffineTransform3D polyToImgTransform = new AffineTransform3D();
		final AffineTransform3D imgToScreenTransform = new AffineTransform3D();
		final double[] auxCoord3D = new double[3];
		final double[] screenCoord = new double[3];
		final double[] screenCoordB = new double[3];
	}

	// ======================== actions - behaviours ========================
	void installBehaviours() {
		//"loose" the annotation site as soon as the BDV's viewport is changed
		bdv.getBdvHandle().getViewerPanel().transformListeners().add( someNewIgnoredTransform -> {
				lostViewOfAnnotationSite();
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
				processPrompt();
			}
		}, "samj_line", "L" );

		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			samjOverlay.toleratedOffViewPlaneDistance += 1.0;
			viewerPanel.getDisplayComponent().repaint();
			System.out.println("Current tolerated view off-plane distance: "+samjOverlay.toleratedOffViewPlaneDistance);
		}, "samj_shorter_view_distance", "shift|D");
		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			samjOverlay.toleratedOffViewPlaneDistance = Math.max(1.0, samjOverlay.toleratedOffViewPlaneDistance - 1.0);
			viewerPanel.getDisplayComponent().repaint();
			System.out.println("Current tolerated view off-plane distance: "+samjOverlay.toleratedOffViewPlaneDistance);
		}, "samj_longer_view_distance", "D");

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

	// ======================== prompts - execution ========================
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


	}

	public Img<T> collectViewPixelData(final Img<T> srcImg) {
		//final RealRandomAccessible<T> srcRealImg = Views.interpolate(Views.extendValue(srcImg, 0), new NearestNeighborInterpolatorFactory<>());
		//final RealRandomAccessible<T> srcRealImg = Views.interpolate(Views.extendValue(srcImg, 0), new NLinearInterpolatorFactory<>());
		final RealRandomAccessible<T> srcRealImg = Views.interpolate(Views.extendValue(srcImg, 0), new ClampingNLinearInterpolatorFactory<>());
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


	private void lostViewOfAnnotationSite() {
	}



	}

	/**
	 */
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
