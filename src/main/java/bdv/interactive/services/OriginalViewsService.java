package bdv.interactive.services;

import bdv.util.BdvHandle;
import bdv.util.MipmapTransforms;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerState;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.interpolation.randomaccess.ClampingNLinearInterpolatorFactory;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Watches a BigDataViewer for any change that alters which pixels (of any source)
 * end up on the screen and where: the viewer transform (panning, zooming,
 * rotating, slicing), the current timepoint, and the size of the viewer canvas.
 * <p>
 * Every such change increments a change counter. A client that captured a view
 * (see {@link #getCurrentView(Source, RealType)}) keeps the counter value that
 * came with it, and later asks {@link #hasChangedSince(long)} to learn if its
 * copy still shows what is on the screen.
 * <p>
 * The counter is a {@code long}: even at a million changes per second it
 * would overflow only after ~292,000 years, so this type has an adequate capacity.
 * <p>
 * This class ignores the display settings ({@code ConverterSetup}) of the
 * sources completely, the captured views carry the original pixel values.
 * See {@link ConvertedViewsService} for the variant that respects them.
 * <p>
 * Not monitored: changes of the source transforms themselves (e.g. via BDV's
 * manual transformation editor) and changes of the pixel content of sources.
 */
public class OriginalViewsService implements AutoCloseable {

	public OriginalViewsService(final BdvHandle bdv) {
		this.bdv = bdv;
		this.viewer = bdv.getViewerPanel();
		this.viewer.state().changeListeners().add(stateListener);
		this.viewer.getDisplayComponent().addComponentListener(resizeListener);
	}

	protected final BdvHandle bdv;
	protected final ViewerPanel viewer;

	public BdvHandle getBdvHandle() { return bdv; }

	/** Unhooks all listeners from the BDV. */
	@Override
	public void close() {
		viewer.state().changeListeners().remove(stateListener);
		viewer.getDisplayComponent().removeComponentListener(resizeListener);
	}

	// ======================== change monitoring ========================
	private final AtomicLong changeCounter = new AtomicLong(0);

	/** The current value of the change counter. */
	public long getChangeCounter() {
		return changeCounter.get();
	}

	/**
	 * @param clientsCounterValue a value previously obtained from {@link #getChangeCounter()}
	 *                            or from {@link CapturedView#getChangeCounter()}
	 * @return true if any monitored change happened since the counter had that value
	 */
	public boolean hasChangedSince(final long clientsCounterValue) {
		return changeCounter.get() != clientsCounterValue;
	}

	/** To be called whenever a monitored change happens. */
	protected void notifyChange() {
		changeCounter.incrementAndGet();
	}

	private final ViewerStateChangeListener stateListener = this::viewerStateChanged;

	protected void viewerStateChanged(final ViewerStateChange change) {
		//unlike in ConvertedViewsService, this method needs not be synchronized
		switch (change) {
			case VIEWER_TRANSFORM_CHANGED:
			case CURRENT_TIMEPOINT_CHANGED:
				notifyChange();
				break;
			default:
				//other changes (visibility, display mode, current source, ...)
				//do not change the pixels of any particular source on the screen
		}
	}

	private final ComponentListener resizeListener = new ComponentAdapter() {
		@Override
		public void componentResized(final ComponentEvent e) {
			notifyChange();
		}
	};

	// ======================== capturing the views ========================
	/**
	 * A view image together with what's needed to interpret it: the change counter
	 * that was valid when the capturing started, and the geometry of the view.
	 */
	public static class CapturedView<T extends RealType<T>> {
		CapturedView(final Img<T> image, final long changeCounter,
		             final AffineTransform3D globalToScreen, final int timepoint, final int mipmapLevel) {
			this.image = image;
			this.changeCounter = changeCounter;
			this.globalToScreen = globalToScreen;
			this.timepoint = timepoint;
			this.mipmapLevel = mipmapLevel;
		}

		private final Img<T> image;
		private final long changeCounter;
		private final AffineTransform3D globalToScreen;
		private final int timepoint;
		private final int mipmapLevel;

		/** 2D image of exactly the size of the viewer canvas, pixel (x,y) is the screen pixel (x,y). */
		public Img<T> getImage() { return image; }
		/** To be used with {@link OriginalViewsService#hasChangedSince(long)}. */
		public long getChangeCounter() { return changeCounter; }
		/** A copy of the viewer transform (global -&gt; screen) used for the capturing. */
		public AffineTransform3D getGlobalToScreenTransform() { return globalToScreen.copy(); }
		/** A copy of the inverse of the viewer transform (screen -&gt; global) used for the capturing. */
		public AffineTransform3D getScreenToGlobalTransform() { return globalToScreen.inverse(); }
		public int getTimepoint() { return timepoint; }
		/** The resolution level of the source the pixels were read from. */
		public int getMipmapLevel() { return mipmapLevel; }
	}

	/**
	 * Captures what's currently on the screen of the given source, using the
	 * source's resolution level that fits best the current zoom, and linear interpolation.
	 * The returned object carries the change counter valid <i>before</i> the
	 * capturing started, so a change during the capturing is noticed by
	 * the {@link #hasChangedSince(long)}.
	 */
	public <OT extends RealType<OT> & NativeType<OT>, IT extends RealType<IT>>
	CapturedView<OT> getCurrentView(final Source<IT> source, final OT outputPixelType) {
		return getCurrentView(source, outputPixelType, -1, Interpolation.NLINEAR);
	}

	/**
	 * @param mipmapLevel the resolution level to read from, or -1 for the best-fitting one
	 */
	public <OT extends RealType<OT> & NativeType<OT>, IT extends RealType<IT>>
	CapturedView<OT> getCurrentView(final Source<IT> source, final OT outputPixelType,
	                                final int mipmapLevel, final Interpolation interpolation) {
		final long counter = getChangeCounter();
		final ViewerState state = viewer.state().snapshot();

		final AffineTransform3D globalToScreen = state.getViewerTransform();
		final int tp = state.getCurrentTimepoint();
		final int level = mipmapLevel >= 0 ? mipmapLevel
				: MipmapTransforms.getBestMipMapLevel(globalToScreen, source, tp);

		// we want: screen -> source pixel grid  ==  (globalToScreen * sourceToGlobal)^-1
		final AffineTransform3D sourceToGlobal = new AffineTransform3D();
		source.getSourceTransform(tp, mipmapLevel, sourceToGlobal);
		sourceToGlobal.preConcatenate(globalToScreen); //means: source -> Global -> Screen
		final AffineTransform3D screenToSource = sourceToGlobal.inverse();

		final Img<OT> img = collectScreenPixels(source.getSource(tp, level),
				screenToSource, interpolation,
				viewer.getDisplayComponent().getWidth(), viewer.getDisplayComponent().getHeight(),
				outputPixelType);

		return new CapturedView<>(img, counter, globalToScreen, tp, level);
	}

	/**
	 * The workhorse: Resamples the source into a 2D image of the given screen size,
	 * such that pixel (x,y) of the output holds the value of the source at the global
	 * position that the viewer transform maps to the screen position (x,y,0).
	 *
	 * @return a zero-filled image if the source is not present at the timepoint
	 * @throws IllegalStateException if the screen size is not positive (e.g. the viewer is not displayed yet)
	 */
	public <OT extends RealType<OT> & NativeType<OT>, IT extends RealType<IT>> Img<OT> collectScreenPixels(
			  final RandomAccessibleInterval<IT> srcImg,
			  final AffineTransform3D screenToSrcImg,
			  final Interpolation interpolation,
			  final int outputWidth, final int outputHeight,
			  final OT outputPixelType) {

		if (outputWidth <= 0 || outputHeight <= 0)
			throw new IllegalStateException("Viewer canvas has no size (" + outputWidth + "x" + outputHeight + ").");

		// prepare the source
		final RealRandomAccessible<IT> srcRealImg =
				interpolation == Interpolation.NLINEAR
						? Views.interpolate(Views.extendValue(srcImg, 0), new ClampingNLinearInterpolatorFactory<>())
						: Views.interpolate(Views.extendValue(srcImg, 0), new NearestNeighborInterpolatorFactory<>());
		final RealRandomAccess<IT> srcRealImgPtr = srcRealImg.realRandomAccess();

		final ArrayImg<OT, ?> screenViewImg = new ArrayImgFactory<>(outputPixelType).create(outputWidth, outputHeight);
		//NB: 2D (not 3D!) image and of the size of the screen -> ArrayImg backend should be enough...
		final Cursor<OT> viewCursor = screenViewImg.localizingCursor();

		final double[] srcImgPos = new double[3];  //orig underlying 3D image
		final double[] screenPos = new double[3];  //the current view 2D image, as a 3D coord though

		while (viewCursor.hasNext()) { //TODO lightly-parallelize this sweep
			OT px = viewCursor.next();
			viewCursor.localize(screenPos);
			screenToSrcImg.apply(screenPos, srcImgPos);
			px.setReal( convert( srcRealImgPtr.setPositionAndGet(srcImgPos).getRealDouble() ) );
		}

		return screenViewImg;
	}

	protected double convert(double in) { return in; }
}
