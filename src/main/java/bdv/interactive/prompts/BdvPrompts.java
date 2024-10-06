package bdv.interactive.prompts;

import bdv.interactive.prompts.planarshapes.PlanarPolygonIn3D;
import bdv.interactive.prompts.planarshapes.PlanarRectangleIn3D;
import bdv.interactive.prompts.views.SpatioTemporalView;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlay;
import bdv.util.BdvStackSource;
import bdv.util.PlaceHolderConverterSetup;
import bdv.util.PlaceHolderOverlayInfo;
import bdv.util.PlaceHolderSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import java.awt.*;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Stack;
import java.util.function.Consumer;

/**
 * @param <IT> pixel type of the input image on which the prompts operate
 * @param <OT> pixel type of the image submitted to the prompts processors
 */
public class BdvPrompts<IT extends RealType<IT>, OT extends RealType<OT> & NativeType<OT>> {
	public BdvPrompts(final RandomAccessibleInterval<IT> operateOnThisImage, final OT promptsPixelType) {
		this(operateOnThisImage, "Input image", "SAMJ prompts", promptsPixelType);
	}

	/** Opens a new BDV over the provided image, and enables this addon in it. */
	public BdvPrompts(final RandomAccessibleInterval<IT> operateOnThisImage, final String imageName, final String overlayName, final OT promptsPixelType) {
		this.annotationSiteImgType = promptsPixelType;
		switchToThisImage(operateOnThisImage);
		final BdvStackSource<IT> bdv = operateOnThisImage.numDimensions() >= 3
				  ? BdvFunctions.show(operateOnThisImage, imageName)
				  : BdvFunctions.show(Views.addDimension(operateOnThisImage,0,0), imageName, BdvOptions.options().is2D());
		this.viewerPanel = bdv.getBdvHandle().getViewerPanel();

		if (viewerPanel.getOptionValues().is2D()) System.out.println("Detected 2D image, switched BDV to the 2D mode.");

		this.samjOverlay = new PromptsAndPolygonsDrawingOverlay();
		BdvFunctions.showOverlay(samjOverlay, overlayName, BdvOptions.options().addTo(bdv))
				  .setColor(new ARGBType( this.samjOverlay.colorPolygons.getRGB() ));

		//"loose" the annotation site as soon as the BDV's viewport is changed
		this.viewerPanel.transformListeners().add( someNewIgnoredTransform -> lostViewOfAnnotationSite() );

		installBehaviours( bdv.getBdvHandle().getTriggerbindings(), true );
	}

	/** Add this addon to an existing BDV instance, and instruct on which source should it operate. */
	public BdvPrompts(final ViewerPanel bdvViewerPanel,
	                  SourceAndConverter<IT> operateOnThisSource,
	                  final TriggerBehaviourBindings bindBehavioursHere,
	                  final String overlayName, final OT promptsPixelType,
	                  final boolean installAlsoUndoRedoKeys) {
		this.viewerPanel = bdvViewerPanel;
		this.annotationSiteImgType = promptsPixelType;
		switchToThisSource(operateOnThisSource);

		this.samjOverlay = new PromptsAndPolygonsDrawingOverlay();
		PlaceHolderSource source = new PlaceHolderSource(overlayName);
		SourceAndConverter<Void> sac = new SourceAndConverter<>(source, null);
		//
		PlaceHolderConverterSetup converterSetup = new PlaceHolderConverterSetup(9999,
				  0, 1, this.samjOverlay.colorPolygons.getRGB());
		System.out.println("converter setup supports color: "+converterSetup.supportsColor());

		PlaceHolderOverlayInfo overlayInfo = new PlaceHolderOverlayInfo(bdvViewerPanel, sac, converterSetup);
		this.samjOverlay.setOverlayInfo( overlayInfo );
		bdvViewerPanel.getDisplay().overlays().add( this.samjOverlay );
		//
		bdvViewerPanel.state().addSource(sac);
		bdvViewerPanel.state().setSourceActive(sac, true);
		//
		DisplayMode currentMode = bdvViewerPanel.state().getDisplayMode();
		if (!currentMode.equals(DisplayMode.FUSED) && !currentMode.equals(DisplayMode.FUSEDGROUP)) {
			bdvViewerPanel.showMessage("Enabling fused mode to display SAMJ overlay.");
			bdvViewerPanel.state().setDisplayMode(DisplayMode.FUSED);
		}

		//"loose" the annotation site as soon as the BDV's viewport is changed
		this.viewerPanel.transformListeners().add( someNewIgnoredTransform -> lostViewOfAnnotationSite() );
		this.viewerPanel.timePointListeners().add( currentTP -> {
			switchToThisSource(operateOnThisSource, currentTP);
		} );

		installBehaviours( bindBehavioursHere, installAlsoUndoRedoKeys );
	}

	private RandomAccessibleInterval<IT> image;
	private final AffineTransform3D imageToGlobalTransform = new AffineTransform3D();
	private final ViewerPanel viewerPanel;

	/** The class registers itself as a polygon consumer,
	 *  and consumes them by showing them in the BDV.
	 *  Returns itself to allow for the calls chaining. */
	public BdvPrompts<IT,OT> enableShowingPolygons() {
		this.addPolygonsConsumer(this.samjOverlay);
		return this;
	}

	public void switchToThisImage(final RandomAccessibleInterval<IT> operateOnThisImage) {
		this.image = operateOnThisImage;
		this.imageToGlobalTransform.identity();
		lostViewOfAnnotationSite();
	}

	public void switchToThisSource(SourceAndConverter<IT> operateOnThisSource) {
		switchToThisSource(operateOnThisSource, viewerPanel.state().getCurrentTimepoint());
	}

	public void switchToThisSource(SourceAndConverter<IT> operateOnThisSource, final int atThisTimepoint) {
		this.image = operateOnThisSource.getSpimSource().getSource(atThisTimepoint, 0);
		operateOnThisSource.getSpimSource().getSourceTransform(atThisTimepoint, 0, this.imageToGlobalTransform);
		lostViewOfAnnotationSite();
	}

	public void addPolygonsConsumer(final Consumer<PlanarPolygonIn3D> consumer) {
		polygonsConsumers.add(consumer);
	}
	public boolean removePolygonsConsumer(final Consumer<PlanarPolygonIn3D> consumer) {
		return polygonsConsumers.remove(consumer);
	}

	public void addPromptsProcessor(final PromptsProcessor<OT> promptToPolygonsGenerator) {
		promptsProcessors.add(promptToPolygonsGenerator);
	}
	public boolean removePromptsProcessor(final PromptsProcessor<OT> promptToPolygonsGenerator) {
		return promptsProcessors.remove(promptToPolygonsGenerator);
	}

	public interface PromptsProcessor <PT extends RealType<PT>> {
		/**
		 * @param prompt The current rectangular/box prompt created by user in BDV.
		 * @param hasViewChangedSinceBefore If false, the image in the 'prompt' is exactly the same as it was provider
		 *                                  in a previous call to this processor. Processor can re-use any previous
		 *                                  own data that depend solely on that 'viewImage2D' image because (again) this
		 *                                  image hasn't changed since the last call.
		 * @return The output list can be of zero length (e.g., when the processor acts as a sink), but must not be null!
		 */
		List<PlanarPolygonIn3D> process(final PlanarRectangleIn3D<PT> prompt, final boolean hasViewChangedSinceBefore);
	}

	private final List< Consumer<PlanarPolygonIn3D> > polygonsConsumers = new ArrayList<>(10);
	private final List< PromptsProcessor<OT> > promptsProcessors = new ArrayList<>(10);

	// ======================== overlay content ========================
	private final PromptsAndPolygonsDrawingOverlay samjOverlay;

	public void stopDrawing() {
		samjOverlay.shouldDrawLine = false;
		samjOverlay.shouldDrawPolygons = false;
	}

	public void startDrawing() {
		samjOverlay.isLineReadyForDrawing = false;
		samjOverlay.shouldDrawLine = true;
		samjOverlay.shouldDrawPolygons = true;
	}

	public void forgetAllPolygons() {
		samjOverlay.tpToCurrPolysList.clear();
		samjOverlay.tpToRedoPolysList.clear();
		viewerPanel.getDisplayComponent().repaint();
	}

	class PromptsAndPolygonsDrawingOverlay extends BdvOverlay implements Consumer<PlanarPolygonIn3D> {
		private int sx,sy; //starting coordinate of the line, the "first end"
		private int ex,ey; //ending coordinate of the line, the "second end"
		protected boolean shouldDrawLine = true;
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

		private Map<Integer, Stack<PlanarPolygonIn3D>> tpToCurrPolysList = new HashMap<>(2000);
		private Map<Integer, Stack<PlanarPolygonIn3D>> tpToRedoPolysList = new HashMap<>(2000);
		//
		private Stack<PlanarPolygonIn3D> getCurrentPolygons() {
			final int tp = viewerPanel.state().getCurrentTimepoint();
			Stack<PlanarPolygonIn3D> retVal = tpToCurrPolysList.get(tp);
			if (retVal == null) {
				retVal = new Stack<>();
				tpToCurrPolysList.put(tp,retVal);
			}
			return retVal;
		}
		private Stack<PlanarPolygonIn3D> getCurrentPolysRedo() {
			final int tp = viewerPanel.state().getCurrentTimepoint();
			Stack<PlanarPolygonIn3D> retVal = tpToRedoPolysList.get(tp);
			if (retVal == null) {
				retVal = new Stack<>();
				tpToRedoPolysList.put(tp,retVal);
			}
			return retVal;
		}
		//
		private void currentPolysUndoOne() {
			Stack<PlanarPolygonIn3D> currPs = getCurrentPolygons();
			if (currPs.isEmpty()) return;
			getCurrentPolysRedo().push( currPs.pop() );
			viewerPanel.getDisplayComponent().repaint();
		}
		private void currentPolysRedoOne() {
			Stack<PlanarPolygonIn3D> redoPs = getCurrentPolysRedo();
			if (redoPs.isEmpty()) return;
			getCurrentPolygons().push( redoPs.pop() );
			viewerPanel.getDisplayComponent().repaint();
		}
		//
		protected boolean shouldDrawPolygons = true;

		@Override
		public void accept(PlanarPolygonIn3D polygon) {
			getCurrentPolygons().add(polygon);
			getCurrentPolysRedo().clear();
		}

		protected final BasicStroke stroke = new BasicStroke( 1.0f ); //lightweight I guess
		protected Color colorPrompt = Color.GREEN;
		protected Color colorPolygons = Color.RED;
		private int colorFromBDV = -1;

		protected double toleratedOffViewPlaneDistance = 6.0;

		@Override
		protected void draw(Graphics2D g) {
			//final double uiScale = UIUtils.getUIScaleFactor( this );
			//final BasicStroke stroke = new BasicStroke( ( float ) uiScale );
			g.setStroke(stroke);

			if (shouldDrawLine && isLineReadyForDrawing) {
				//draws the line
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
				g.setPaint(colorPolygons);
				viewerPanel.state().getViewerTransform(imgToScreenTransform);
				boolean isCloseToViewingPlane = true, isCloseToViewingPlaneB = true;
				final List<PlanarPolygonIn3D> polygonList = getCurrentPolygons();
				for (PlanarPolygonIn3D p : polygonList) {
					p.getTransformTo3d(polyToImgTransform);
					polyToImgTransform.preConcatenate(imgToScreenTransform);
					//TODO: don't loop if bbox is already far away
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

		//mem placeholders to avoid repeating calls to new()
		final AffineTransform3D polyToImgTransform = new AffineTransform3D();
		final AffineTransform3D imgToScreenTransform = new AffineTransform3D();
		final double[] auxCoord3D = new double[3];
		final double[] screenCoord = new double[3];
		final double[] screenCoordB = new double[3];
	}

	// ======================== actions - behaviours ========================
	protected void installBehaviours(final TriggerBehaviourBindings bindThemHere,
	                                 final boolean installAlsoUndoRedoKeys) {
		final Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bindThemHere, "bdvprompts" );

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

				processRectanglePrompt();
			}
		}, "bdvprompts_rectangle", "L" );

		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			samjOverlay.toleratedOffViewPlaneDistance += 1.0;
			viewerPanel.getDisplayComponent().repaint();
			System.out.println("Current tolerated view off-plane distance: "+samjOverlay.toleratedOffViewPlaneDistance);
		}, "bdvprompts_shorter_view_distance", "shift|D");
		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			samjOverlay.toleratedOffViewPlaneDistance = Math.max(1.0, samjOverlay.toleratedOffViewPlaneDistance - 1.0);
			viewerPanel.getDisplayComponent().repaint();
			System.out.println("Current tolerated view off-plane distance: "+samjOverlay.toleratedOffViewPlaneDistance);
		}, "bdvprompts_longer_view_distance", "D");

		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			if (annotationSites.isEmpty()) {
				System.out.println("Switching NOT... no annotation sites available yet.");
				return;
			}
			int nextSiteId = lastVisitedAnnotationSiteId + 1;
			if (nextSiteId > annotationSites.size()) nextSiteId = 1;
			System.out.println("Switching to annotation site: "+nextSiteId);
			displayAnnotationSite(nextSiteId);
		}, "bdvprompts_next_view", "W");

		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			if (annotationSites.isEmpty()) {
				System.out.println("Switching NOT... no annotation sites available yet.");
				return;
			}
			System.out.println("Switching to last visited annotation site: "+lastVisitedAnnotationSiteId);
			displayAnnotationSite(lastVisitedAnnotationSiteId);
		}, "bdvprompts_last_view", "shift|W");

		if (installAlsoUndoRedoKeys) {
			behaviours.behaviour((ClickBehaviour) (x, y) -> samjOverlay.currentPolysUndoOne(),
			"bdvprompts_undo", "U");
			behaviours.behaviour((ClickBehaviour) (x, y) -> samjOverlay.currentPolysRedoOne(),
			"bdvprompts_redo", "shift|U");
		}
	}

	// ======================== prompts - execution ========================
	private void processRectanglePrompt() {
		final boolean isNewViewImage = isNextPromptOnNewAnnotationSite;
		if (isNextPromptOnNewAnnotationSite) installNewAnnotationSite();

		//create prompt with coords w.r.t. the annotation site image
		PlanarRectangleIn3D<OT> prompt = new PlanarRectangleIn3D<>(
				  this.annotationSiteViewImg,
				  this.viewerPanel.state().getViewerTransform().inverse());
		samjOverlay.normalizeLineEnds();
		prompt.setDiagonal(samjOverlay.sx,samjOverlay.sy, samjOverlay.ex,samjOverlay.ey);

		//submit the prompt to polygons processors (producers, in fact)
		final List<PlanarPolygonIn3D> obtainedPolygons = new ArrayList<>(500);
		promptsProcessors.forEach( p -> obtainedPolygons.addAll( p.process(prompt, isNewViewImage) ) );

		//submit the created polygons to the polygon consumers
		obtainedPolygons.forEach( poly -> polygonsConsumers.forEach(c -> c.accept(poly)) );

		//request redraw, just in case after all polygons are consumed,
		//and also to make sure the prompt rectangle disappears
		viewerPanel.getDisplayComponent().repaint();
	}

	// ======================== prompts - image data ========================
	private final OT annotationSiteImgType;
	private Img<OT> annotationSiteViewImg;

	//aux (and to avoid repetitive new() calls) for the collectViewPixelData() below:
	private final double[] srcImgPos = new double[3];  //orig underlying 3D image
	private final double[] screenPos = new double[3];  //the current view 2D image, as a 3D coord though
	private final AffineTransform3D imgToScreenTransform = new AffineTransform3D();

	protected Img<OT> collectViewPixelData(final RandomAccessibleInterval<IT> srcImg) {
		final RealRandomAccessible<IT> srcRealImg = Views.interpolate(Views.extendValue(srcImg, 0), new ClampingNLinearInterpolatorFactory<>());
		final RealRandomAccess<IT> srcRealImgPtr = srcRealImg.realRandomAccess();

		final Dimension displaySize = viewerPanel.getDisplayComponent().getSize();
		ArrayImg<OT, ?> viewImg = new ArrayImgFactory<>(annotationSiteImgType).create(displaySize.width, displaySize.height);
		//NB: 2D (not 3D!) image and of the size of the screen -> ArrayImg backend should be enough...
		Cursor<OT> viewCursor = viewImg.localizingCursor();

		viewerPanel.state().getViewerTransform(imgToScreenTransform);
		screenPos[2] = 0.0; //to be on the safe side

		while (viewCursor.hasNext()) {
			OT px = viewCursor.next();
			viewCursor.localize(screenPos);
			imgToScreenTransform.applyInverse(srcImgPos, screenPos); //NB: Inverse has also "reversed" order of arguments!
			imageToGlobalTransform.applyInverse(srcImgPos, srcImgPos);
			px.setReal( srcRealImgPtr.setPositionAndGet(srcImgPos).getRealDouble() );
		}

		return viewImg;
	}

	// ======================== prompts - annotation sites ========================
	/** Basically, flags that the encoding is no longer valid */
	private boolean isNextPromptOnNewAnnotationSite = true;

	private void lostViewOfAnnotationSite() {
		isNextPromptOnNewAnnotationSite = true;
	}

	private void installNewAnnotationSite() {
		//register the new site's data
		final int newIdx = annotationSites.size()+1;
		annotationSites.put(newIdx, new SpatioTemporalView(this.viewerPanel));
		lastVisitedAnnotationSiteId = newIdx;

		annotationSiteViewImg = collectViewPixelData(this.image);
		isNextPromptOnNewAnnotationSite = false;
	}

	/**
	 * @param id ID of the requested annotation site.
	 * @return False if the requested site is not available, and thus no action was taken.
	 */
	private boolean displayAnnotationSite(int id) {
		if (!annotationSites.containsKey(id)) return false;

		//NB: if the switch would lead to a new annotation site, the monitor
		//    of the rendering will call this.lostViewOfAnnotationSite(), but
		//    if the switch has no visible effect, we could continue with the
		//    current annotation site data (esp. with this.annotationSiteViewImg)
		annotationSites.get(id).applyOnThis(this.viewerPanel);
		lastVisitedAnnotationSiteId = id;
		return true;
	}

	//maps internal ID of a view (which was registered with the key to start SAMJ Annotation) to
	//an object that represents that exact view, and another map for polygons associated with that view
	private final Map<Integer, SpatioTemporalView> annotationSites = new HashMap<>(100);
	private int lastVisitedAnnotationSiteId = -1;
}
