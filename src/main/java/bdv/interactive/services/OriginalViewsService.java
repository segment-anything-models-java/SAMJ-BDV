package bdv.interactive.services;

import bdv.util.BdvHandle;
import bdv.util.MipmapTransforms;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerState;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import net.imglib2.RandomAccess;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.IntStream;

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
 * would overflow only after ~292,000 years, so a pair of counters is not needed.
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
	public static class CapturedView<O> {
		CapturedView(final Img<O> image, final long changeCounter,
		             final AffineTransform3D globalToScreen, final int timepoint, final int mipmapLevel) {
			this.image = image;
			this.changeCounter = changeCounter;
			this.globalToScreen = globalToScreen;
			this.timepoint = timepoint;
			this.mipmapLevel = mipmapLevel;
		}

		private final Img<O> image;
		private final long changeCounter;
		private final AffineTransform3D globalToScreen;
		private final int timepoint;
		private final int mipmapLevel;

		/** 2D image of exactly the size of the viewer canvas, pixel (x,y) is the screen pixel (x,y). */
		public Img<O> getImage() { return image; }
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
	public <O extends RealType<O> & NativeType<O>>
	CapturedView<O> getCurrentView(final Source<? extends RealType<?>> source, final O outputPixelType) {
		return getCurrentView(source, outputPixelType, -1, Interpolation.NLINEAR, null);
	}

	/**
	 * @param mipmapLevel the resolution level to read from, or -1 for the best-fitting one
	 * @param valueMapper applied on every pixel value before it is stored, null means identity
	 */
	public <O extends RealType<O> & NativeType<O>>
	CapturedView<O> getCurrentView(final Source<? extends RealType<?>> source, final O outputPixelType,
	                               final int mipmapLevel, final Interpolation interpolation,
	                               final DoubleUnaryOperator valueMapper) {
		final long counter = getChangeCounter();
		final ViewerState state = viewer.state().snapshot();
		final Component canvas = viewer.getDisplayComponent();

		final AffineTransform3D globalToScreen = state.getViewerTransform();
		final int tp = state.getCurrentTimepoint();
		final int level = mipmapLevel >= 0 ? mipmapLevel
				: MipmapTransforms.getBestMipMapLevel(globalToScreen, source, tp);

		final Img<O> img = captureView(source, tp, level, interpolation,
				globalToScreen, canvas.getWidth(), canvas.getHeight(),
				outputPixelType, valueMapper);
		return new CapturedView<>(img, counter, globalToScreen, tp, level);
	}

	/** Captures what's currently on the screen of the given source; just the image, no change counter. */
	public static <O extends RealType<O> & NativeType<O>>
	Img<O> captureCurrentView(final BdvHandle bdv, final Source<? extends RealType<?>> source, final O outputPixelType) {
		final ViewerPanel viewer = bdv.getViewerPanel();
		final ViewerState state = viewer.state().snapshot();
		final AffineTransform3D globalToScreen = state.getViewerTransform();
		final int tp = state.getCurrentTimepoint();
		final int level = MipmapTransforms.getBestMipMapLevel(globalToScreen, source, tp);
		return captureView(source, tp, level, Interpolation.NLINEAR, globalToScreen,
				viewer.getDisplayComponent().getWidth(), viewer.getDisplayComponent().getHeight(),
				outputPixelType, null);
	}

	/**
	 * The workhorse: Resamples the source into a 2D image of the given screen size,
	 * such that pixel (x,y) of the output holds the value of the source at the global
	 * position that the viewer transform maps to the screen position (x,y,0).
	 * Rows are processed in parallel.
	 *
	 * @param globalToScreen the viewer transform (as reported by the {@code ViewerState})
	 * @param valueMapper applied on every pixel value before it is stored, null means identity
	 * @return a zero-filled image if the source is not present at the timepoint
	 * @throws IllegalStateException if the screen size is not positive (e.g. the viewer is not displayed yet)
	 */
	public static <O extends RealType<O> & NativeType<O>>
	Img<O> captureView(final Source<? extends RealType<?>> source,
	                   final int timepoint, final int mipmapLevel, final Interpolation interpolation,
	                   final AffineTransform3D globalToScreen, final int screenWidth, final int screenHeight,
	                   final O outputPixelType, final DoubleUnaryOperator valueMapper) {
		if (screenWidth <= 0 || screenHeight <= 0)
			throw new IllegalStateException("Viewer canvas has no size (" + screenWidth + "x" + screenHeight + ").");

		final Img<O> viewImg = new ArrayImgFactory<>(outputPixelType).create(screenWidth, screenHeight);
		if (!source.isPresent(timepoint)) return viewImg;

		final RealRandomAccessible<? extends RealType<?>> srcImg =
				source.getInterpolatedSource(timepoint, mipmapLevel, interpolation);

		// we want: screen -> source pixel grid  ==  (globalToScreen * sourceToGlobal)^-1
		final AffineTransform3D sourceToScreen = new AffineTransform3D();
		source.getSourceTransform(timepoint, mipmapLevel, sourceToScreen);
		sourceToScreen.preConcatenate(globalToScreen);
		final AffineTransform3D screenToSource = sourceToScreen.inverse();

		// moving by one screen pixel along x is moving by this vector in the source
		final double[] stepX = new double[] {
				screenToSource.get(0, 0), screenToSource.get(1, 0), screenToSource.get(2, 0) };

		IntStream.range(0, screenHeight).parallel().forEach(y -> {
			final RealRandomAccess<? extends RealType<?>> srcPtr = srcImg.realRandomAccess();
			final RandomAccess<O> outPtr = viewImg.randomAccess();
			final double[] pos = new double[3];
			screenToSource.apply(new double[] {0, y, 0}, pos);
			outPtr.setPosition(0, 0);
			outPtr.setPosition(y, 1);

			for (int x = 0; x < screenWidth; ++x) {
				srcPtr.setPosition(pos);
				final double v = srcPtr.get().getRealDouble();
				outPtr.get().setReal(valueMapper == null ? v : valueMapper.applyAsDouble(v));
				outPtr.fwd(0);
				pos[0] += stepX[0];
				pos[1] += stepX[1];
				pos[2] += stepX[2];
			}
		});

		return viewImg;
	}
}
