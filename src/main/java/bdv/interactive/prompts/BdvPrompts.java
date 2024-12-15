package bdv.interactive.prompts;

import ai.nets.samj.util.PlanarShapesRasterizer;
import ai.nets.samj.util.Prompts;
import bdv.interactive.prompts.planarshapes.PlanarPolygonIn3D;
import bdv.interactive.prompts.planarshapes.PlanarRectangleIn3D;
import bdv.interactive.prompts.views.SpatioTemporalView;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlay;
import bdv.util.BdvStackSource;
import bdv.util.PlaceHolderConverterSetup;
import bdv.util.PlaceHolderOverlayInfo;
import bdv.util.PlaceHolderSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import bdv.viewer.ViewerPanel;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.planar.PlanarImgs;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 *
 * A note on coordinate systems:
 * The central (Cartesian) coordinate system (shorthand: "coords") is a 'global' one, which
 * the BDV uses intrinsically. It is the coords that one can read in the top-right corner of
 * the BDV display, it is this one where individual sources meet - into which they are mapped.
 *
 * Then there is a source coords for each source/original image. This one typically aligns with
 * the pixel grid of the source/image. So, a transformation between this coords and the global
 * coords involves pixel size, anisotropy and potential positioning of this source/image.
 * This is what the source.getSpimSource().getSourceTransform() reports.
 *
 * The current slicing through the volume(s) in BDV, the current view (aka viewer), the current
 * screen content, this is realized/rendered into an 2D image that is "sent" to the screen.
 * This image's width and height thus matches exactly the pixel width and height of the
 * BDV's main display window. This is what the bdv.state().getViewerTransform() reports.
 *
 * So, these are the three coords: raw image data -to- global -to- some view/slicing.
 *
 * The annotation images belong to the "some view" coord, while polygons are defined
 * using the global coords. These two, in fact, the transformation between these two,
 * are important for creating prompts and displaying of polygons. When polygons are to
 * be rendered to the underlying original raw image data, the transformation between this
 * (source pixel grid) and the global coords becomes important.
 *
 *
 * @param <IT> pixel type of the input image on which the prompts operate
 * @param <OT> pixel type of the image submitted to the prompts processors
 */
public class BdvPrompts<IT extends RealType<IT>, OT extends RealType<OT> & NativeType<OT>> {
	public BdvPrompts(final RandomAccessibleInterval<IT> operateOnThisImage, final OT promptsPixelType) {
		this(operateOnThisImage, "Input image", "SAMJ", promptsPixelType);
	}

	/** Opens a new BDV over the provided image, and enables this addon in it. */
	public BdvPrompts(final RandomAccessibleInterval<IT> operateOnThisImage, final String imageName,
	                  final String overlayName, final OT promptsPixelType) {
		this(operateOnThisImage, imageName, null, null, overlayName, promptsPixelType);
	}

	/** Opens a new BDV over the two provided images using the first one for SAM while the second one
	    is there really only for the viewing pleasure; and enables this addon in the BDV. */
	public BdvPrompts(final RandomAccessibleInterval<IT> operateOnThisImage, final String imageName,
	                  final RandomAccessibleInterval<IT> displayAlsoThisImage, final String imageName2,
	                  final String overlayName, final OT promptsPixelType) {
		this.annotationSiteImgType = promptsPixelType;
		switchToThisImage(operateOnThisImage);
		final BdvStackSource<IT> bdv = operateOnThisImage.numDimensions() >= 3
				  ? BdvFunctions.show(operateOnThisImage, imageName)
				  : BdvFunctions.show(Views.addDimension(operateOnThisImage,0,0), imageName, BdvOptions.options().is2D());
		this.viewerPanel = bdv.getBdvHandle().getViewerPanel();
		this.viewerConverterSetup = bdv.getConverterSetups().get(0);

		if (displayAlsoThisImage != null && imageName2 != null) {
			if (displayAlsoThisImage.numDimensions() >= 3) {
				BdvFunctions.show(displayAlsoThisImage, imageName2, BdvOptions.options().addTo(bdv));
			} else {
				BdvFunctions.show(Views.addDimension(displayAlsoThisImage,0,0), imageName2, BdvOptions.options().is2D().addTo(bdv));
			}
		}

		if (viewerPanel.getOptionValues().is2D()) System.out.println("Detected 2D image, switched BDV to the 2D mode.");

		this.samjOverlay = new PromptsAndPolygonsDrawingOverlay();
		BdvFunctions.showOverlay(samjOverlay, overlayName, BdvOptions.options().addTo(bdv))
				  .setColor(new ARGBType( this.samjOverlay.colorPolygons.getRGB() ));

		installGroupsOrFusedMode(this.viewerPanel, false);

		//"loose" the annotation site as soon as the BDV's viewport is changed
		this.viewerPanel.transformListeners().add( someNewIgnoredTransform -> lostViewOfAnnotationSite() );
		this.viewerConverterSetup.setupChangeListeners().add( cs -> notifyContrastSettingsChanged() );

		installBasicBehaviours( bdv.getBdvHandle().getTriggerbindings(), true );
	}

	protected void installGroupsOrFusedMode(final ViewerPanel viewerPanel, final boolean requestingFusedMode) {
		final List<SourceAndConverter<?>> srcs = viewerPanel.state().getSources();
		final int overlaySrcIdx = srcs.size()-1;

		//is the input image given twice?
		if (overlaySrcIdx == 2 && !requestingFusedMode) {
			//yes, so we go for groups:
			//add overlay to the first group
			final List<SourceGroup> grps = viewerPanel.state().getGroups();
			viewerPanel.state().addSourceToGroup(srcs.get(overlaySrcIdx), grps.get(0));

			//add 2nd src and overlay to the second group, if 2nd src available
			viewerPanel.state().addSourceToGroup(srcs.get(1), grps.get(1));
			viewerPanel.state().addSourceToGroup(srcs.get(overlaySrcIdx), grps.get(1));

			viewerPanel.showMessage("Enabling GROUP MODE to display SAMJ overlay.");
			viewerPanel.state().setDisplayMode(DisplayMode.GROUP);
		} else {
			//no, in which case no need for groups, just enable the sources fused mode
			viewerPanel.showMessage("Enabling FUSED MODE to display SAMJ overlay.");
			viewerPanel.state().setDisplayMode(DisplayMode.FUSED);
		}
	}


	/** Add this addon to an existing BDV instance, and instruct on which source should it operate. */
	public BdvPrompts(final ViewerPanel bdvViewerPanel,
	                  SourceAndConverter<IT> operateOnThisSource,
	                  ConverterSetup associatedConverterSetup,
	                  final TriggerBehaviourBindings bindBehavioursHere,
	                  final String overlayName, final OT promptsPixelType,
	                  final boolean installAlsoUndoRedoKeys) {
		this.viewerPanel = bdvViewerPanel;
		this.annotationSiteImgType = promptsPixelType;
		switchToThisSource(operateOnThisSource, associatedConverterSetup);

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
		installGroupsOrFusedMode(bdvViewerPanel, true);

		//"loose" the annotation site as soon as the BDV's viewport is changed
		this.viewerPanel.transformListeners().add( someNewIgnoredTransform -> lostViewOfAnnotationSite() );
		this.viewerPanel.timePointListeners().add( currentTP -> {
			switchToThisSource(operateOnThisSource, associatedConverterSetup, currentTP);
		} );

		installBasicBehaviours( bindBehavioursHere, installAlsoUndoRedoKeys );
	}

	private RandomAccessibleInterval<IT> image;
	private final AffineTransform3D imageToGlobalTransform = new AffineTransform3D();
	private final ViewerPanel viewerPanel;
	private ConverterSetup viewerConverterSetup;

	final Behaviours behaviours = new Behaviours( new InputTriggerConfig() );

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

	public void switchToThisSource(SourceAndConverter<IT> operateOnThisSource,
	                               ConverterSetup associatedConverterSetup) {
		switchToThisSource(operateOnThisSource, associatedConverterSetup, viewerPanel.state().getCurrentTimepoint());
	}

	public void switchToThisSource(SourceAndConverter<IT> operateOnThisSource,
	                               ConverterSetup associatedConverterSetup,
	                               final int atThisTimepoint) {
		this.image = operateOnThisSource.getSpimSource().getSource(atThisTimepoint, 0);
		operateOnThisSource.getSpimSource().getSourceTransform(atThisTimepoint, 0, this.imageToGlobalTransform);
		this.viewerConverterSetup = associatedConverterSetup;
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

	public interface PromptsProcessor <PT extends RealType<PT> & NativeType<PT>> {
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
				viewerPanel.state().getViewerTransform(globalToScreenTransform); //this is a global -> view/screen
				boolean isCloseToViewingPlane = true, isCloseToViewingPlaneB = true;
				final List<PlanarPolygonIn3D> polygonList = getCurrentPolygons();
				for (PlanarPolygonIn3D p : polygonList) {
					p.getTransformTo3d(polyToGlobalTransform);
					polyToGlobalTransform.preConcatenate(globalToScreenTransform);
					//TODO: don't loop if bbox is already far away
					//... when measuring the rendering times, it turned out that walking through polygons'
					//    vertices even to realize that the vertices are off-screen (either laterally or
					//    axially/depth) takes close to no-time; the expensive part is only the actual
					//    drawing of line segments on the screen; so doing additional tests if polygon is
					//    laterally within the screen (the TODO below) or within the "focus depth" (this TODO)
					//    gains no benefit
					for (int i = 0; i <= p.size(); i++) {
						//NB: the first (i=0) point is repeated to close the loop
						p.coordinate2D(i % p.size(), auxCoord3D);
						if (i % 2 == 0) {
							polyToGlobalTransform.apply(auxCoord3D, screenCoord);
							isCloseToViewingPlane = Math.abs(screenCoord[2]) < toleratedOffViewPlaneDistance;
						} else {
							polyToGlobalTransform.apply(auxCoord3D, screenCoordB);
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
		final AffineTransform3D polyToGlobalTransform = new AffineTransform3D();
		final AffineTransform3D globalToScreenTransform = new AffineTransform3D();
		final double[] auxCoord3D = new double[3];
		final double[] screenCoord = new double[3];
		final double[] screenCoordB = new double[3];
	}

	// ======================== actions - behaviours ========================
	class DragBehaviourSkeleton implements DragBehaviour {
		DragBehaviourSkeleton(RectanglePromptProcessor localPromptMethodRef, boolean shouldApplyContrastSetting) {
			this.methodThatProcessesRectanglePrompt = localPromptMethodRef;
			this.considerCurrentContrastSetting = shouldApplyContrastSetting;
		}

		final RectanglePromptProcessor methodThatProcessesRectanglePrompt;
		final boolean considerCurrentContrastSetting;

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
			samjOverlay.normalizeLineEnds();
			handleRectanglePrompt();
		}

		void handleRectanglePrompt() {
			applyContrastSetting_prevValue = applyContrastSetting_currValue;
			applyContrastSetting_currValue = this.considerCurrentContrastSetting;

			final boolean isNewViewImage = isNextPromptOnNewAnnotationSite
					  || (applyContrastSetting_currValue && isNextPromptOnChangedContrast)
					  || (applyContrastSetting_prevValue != applyContrastSetting_currValue);
			if (isNewViewImage) installNewAnnotationSite();
			this.methodThatProcessesRectanglePrompt.apply( isNewViewImage );
		}
	}

	protected void installBasicBehaviours(final TriggerBehaviourBindings bindThemHere,
	                                      final boolean installAlsoUndoRedoKeys) {
		behaviours.install( bindThemHere, "bdv_samj_prompts" );

		//install behaviour for moving a line in the BDV view, with shortcut "L"
		behaviours.behaviour( new DragBehaviourSkeleton(this::processRectanglePrompt, false),
				  "bdvprompts_rectangle_samj_orig", "L" );
		behaviours.behaviour( new DragBehaviourSkeleton(this::processRectanglePrompt, true),
				  "bdvprompts_rectangle_samj_contrast", "K" );

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

		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			Img<UnsignedShortType> maskImage = PlanarImgs.unsignedShorts(this.image.dimensionsAsLongArray());
			final ExtendedRandomAccessibleInterval<UnsignedShortType, Img<UnsignedShortType>> extMaskImage = Views.extendValue(maskImage, 0);
			//
			PlanarShapesRasterizer rasterizer = new PlanarShapesRasterizer();
			AtomicInteger value = new AtomicInteger(0);
			samjOverlay.getCurrentPolygons()
				//.parallelStream() -- rasterizer is not re-entrant-safe, pool of them would be needed
				.forEach(polygon -> {
					int drawValue = value.addAndGet(1);
					System.out.println("Rendering polygon no. "+drawValue);
					rasterizer.rasterizeIntoImg(polygon,this.imageToGlobalTransform.inverse(), extMaskImage, drawValue);
				});
			ImageJFunctions.show(maskImage, "SAMJ BDV masks");
		}, "bdvprompts_export", "Y");
	}

	public void installDefaultMultiPromptBehaviour() {
		installOwnMultiPromptBehaviour(Prompts::getSeedsByContrastThresholdingAndClosing,
				"bdvprompts_rectangle_thres_seeds",
				"J");
	}

	public void installOwnMultiPromptBehaviour(final SeedsFromPromptCreator<OT> seedsCreator,
	                                           String actionName, String actionTriggers) {
		behaviours.behaviour(
			new DragBehaviourSkeleton(
				isNewAnnotationImageInstalled -> findSeedsAndProcessAsRectanglePrompts(seedsCreator, isNewAnnotationImageInstalled),
				false //NB: the seedsCreator will see the current contrast setting, and the prompts
				      //    shall be applied on the original (unaltered) input image -> thus 'false' here
			),
			actionName,
			actionTriggers
		);
	}

	// ======================== prompts - execution ========================
	/**
	 * A local iface to be able to plug functions to the {@link DragBehaviourSkeleton}
	 */
	interface RectanglePromptProcessor {
		void apply(boolean isNewAnnotationImageInstalled);
	}

	public interface SeedsFromPromptCreator<OT> {
		/**
		 * Reads the input image, while possibly on-the-fly recalculate the image pixel
		 * values according to the user's current contrast-adjustment. The implementing
		 * function is expected to {@link ImageJFunctions#show(RandomAccessibleInterval)}
		 * images at various stage of processing, if they are available.
		 *
		 * @param inputImageToEstablishSeedsHere A image that's exactly the user selected rectangle,
		 *                                       in which she wants to have seeds detected
		 * @param considerThisIntensityScaling  Optionally to use an information about the current contrast-setting
		 * @param bitFieldForRequestedDebugImages See {@link Prompts#SHOW_NO_DBGIMAGES} and nearby bit-markers
		 */
		RandomAccessibleInterval<OT> establishBinarySeeds(
				  final RandomAccessibleInterval<OT> inputImageToEstablishSeedsHere,
				  final ConverterSetup considerThisIntensityScaling,
				  final int bitFieldForRequestedDebugImages);
	}

	private void processRectanglePrompt(boolean isNewAnnotationImageInstalled) {
		//create prompt with coords w.r.t. the annotation site image
		PlanarRectangleIn3D<OT> prompt = new PlanarRectangleIn3D<>(
				  this.annotationSiteViewImg,
				  this.viewerPanel.state().getViewerTransform().inverse()); //view -> global coords
				  //NB: viewerPanel.state().getViewerTransform() is giving global to view(er)/screen
		prompt.setDiagonal(samjOverlay.sx,samjOverlay.sy, samjOverlay.ex,samjOverlay.ey);

		doOnePrompt(prompt, isNewAnnotationImageInstalled);
	}

	private void doOnePrompt(PlanarRectangleIn3D<OT> prompt, boolean isNewAnnotationImageInstalled) {
		//submit the prompt to polygons processors (producers, in fact)
		final List<PlanarPolygonIn3D> obtainedPolygons = new ArrayList<>(500);
		promptsProcessors.forEach( p -> obtainedPolygons.addAll( p.process(prompt, isNewAnnotationImageInstalled) ) );

		//submit the created polygons to the polygon consumers
		obtainedPolygons.forEach( poly -> polygonsConsumers.forEach(c -> c.accept(poly)) );

		//request redraw, just in case after all polygons are consumed,
		//and also to make sure the prompt rectangle disappears
		viewerPanel.getDisplayComponent().repaint();
	}

	private void findSeedsAndProcessAsRectanglePrompts(SeedsFromPromptCreator<OT> seedsCreator,
	                                                   boolean isNewAnnotationImageInstalled) {
		final FinalInterval roi = new FinalInterval(
				new long[] {samjOverlay.sx,samjOverlay.sy},
				new long[] {samjOverlay.ex,samjOverlay.ey}
			);

		Prompts.increaseDebugImagesCounter();
		RandomAccessibleInterval<OT> seedsRAI = seedsCreator.establishBinarySeeds(
				Views.interval(this.annotationSiteViewImg, roi),
				this.viewerConverterSetup,
				this.multiPromptsDebugBitField
			);

		List<int[]> seeds = Prompts.returnSeedsAsBoxes(seedsRAI, this.multiPromptsDebugBitField);

		final PlanarRectangleIn3D<OT> prompt = new PlanarRectangleIn3D<>(
				this.annotationSiteViewImg,
				this.viewerPanel.state().getViewerTransform().inverse() //view -> global coords
				//NB: viewerPanel.state().getViewerTransform() is giving global to view(er)/screen
			);

		//NB: boxes are in 'roi' local coords, we need coords of 'annotationSiteViewImg'
		final int x_offset = (int)roi.min(0);
		final int y_offset = (int)roi.min(1);

		boolean isNewImage = isNewAnnotationImageInstalled;
		for (int[] box : seeds) {
			prompt.resetDiagonal(box[0]+x_offset,box[1]+y_offset, box[2]+x_offset,box[3]+y_offset);
			doOnePrompt(prompt, isNewImage);
			isNewImage = false;
			//NB: consecutive calls here operate on the same image, thus we can do this
		}
	}

	public int multiPromptsDebugBitField = Prompts.SHOW_NO_DBGIMAGES;
	public void setMultiPromptsNoDebug() { this.multiPromptsDebugBitField = Prompts.giveBitFlagForNoDebug(); }
	public void setMultiPromptsSrcOnlyDebug() { this.multiPromptsDebugBitField = Prompts.giveBitFlagForSrcOnlyDebug(); }
	public void setMultiPromptsMildDebug() { this.multiPromptsDebugBitField = Prompts.giveBitFlagForMildDebug(); }
	public void setMultiPromptsFullDebug() { this.multiPromptsDebugBitField = Prompts.giveBitFlagForFullDebug(); }

	// ======================== prompts - image data ========================
	private final OT annotationSiteImgType;
	private Img<OT> annotationSiteViewImg;

	private boolean applyContrastSetting_prevValue = false;
	private boolean applyContrastSetting_currValue = false;

	//aux (and to avoid repetitive new() calls) for the collectViewPixelData() below:
	private final double[] srcImgPos = new double[3];  //orig underlying 3D image
	private final double[] screenPos = new double[3];  //the current view 2D image, as a 3D coord though
	private final AffineTransform3D imgToScreenAuxTransform = new AffineTransform3D();

	protected Img<OT> collectViewPixelData(final RandomAccessibleInterval<IT> srcImg) {
		final RealRandomAccessible<IT> srcRealImg = Views.interpolate(Views.extendValue(srcImg, 0), new ClampingNLinearInterpolatorFactory<>());
		final RealRandomAccess<IT> srcRealImgPtr = srcRealImg.realRandomAccess();

		System.out.println("New annotation site, collecting pixels from "+((Interval)srcImg).toString());

		final Dimension displaySize = viewerPanel.getDisplayComponent().getSize();
		ArrayImg<OT, ?> viewImg = new ArrayImgFactory<>(annotationSiteImgType).create(displaySize.width, displaySize.height);
		//NB: 2D (not 3D!) image and of the size of the screen -> ArrayImg backend should be enough...
		Cursor<OT> viewCursor = viewImg.localizingCursor();

		//NB: viewerPanel.state().getViewerTransform() is giving global to (current) view(er) == the screen content
		viewerPanel.state().getViewerTransform(imgToScreenAuxTransform); // A
		//                                                                  B = imageToGlobalTransform
		// taking AB gives orig_image to screen, but inverse is wanted,
		// taking inverse  (AB)^-1 = B^-1 A^-1  gives the wanted screen to image (through global coord system),
		// so B needs to come after A (not preConcatenate())
		imgToScreenAuxTransform.concatenate(imageToGlobalTransform);
		screenPos[2] = 0.0; //to be on the safe side

		while (viewCursor.hasNext()) {
			OT px = viewCursor.next();
			viewCursor.localize(screenPos);
			imgToScreenAuxTransform.applyInverse(srcImgPos, screenPos); //NB: Inverse has also "reversed" order of arguments!
			px.setReal( srcRealImgPtr.setPositionAndGet(srcImgPos).getRealDouble() );
		}

		return viewImg;
	}

	// ======================== prompts - annotation sites ========================
	/** Basically, flags that the encoding is no longer valid */
	private boolean isNextPromptOnChangedContrast = true;

	private void notifyContrastSettingsChanged() {
		isNextPromptOnChangedContrast = true;
	}

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

		if (applyContrastSetting_currValue) {
			final double min = this.viewerConverterSetup.getDisplayRangeMin();
			double max = this.viewerConverterSetup.getDisplayRangeMax();
			System.out.println("Massaging annotation view image between min = "+min+" and max = "+max);
			if (max == min) max += 1.0;

			final double range = max - min;
			annotationSiteViewImg.forEach( px -> px.setReal(Math.min(Math.max(px.getRealDouble() - min, 0.0) / range, 1.0)) );
		}
		isNextPromptOnChangedContrast = false;
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
