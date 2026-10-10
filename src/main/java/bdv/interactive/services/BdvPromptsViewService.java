package bdv.interactive.services;

import bdv.util.BdvHandle;
import bdv.util.MipmapTransforms;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
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

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleUnaryOperator;

/**
 * A common backbone of the various monitors for view changes. The different monitors
 * vary in what they are sensitive to, and how much.
 */
public abstract class BdvPromptsViewService {

	public BdvPromptsViewService(final BdvHandle bdv) {
		this.bdv = bdv;
		this.viewer = bdv.getViewerPanel();
	}

	protected final BdvHandle bdv;
	protected final ViewerPanel viewer;

	public BdvHandle getBdvHandle() { return bdv; }

	// ======================== change monitoring ========================
	protected final AtomicLong changeCounter = new AtomicLong(0);

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

	// ======================== capturing the views ========================
	/**
	 * A view image together with its "provenance", that is the resolution from
	 * which it was pulled, at which timepoint, and under what geometry of the view.
	 */
	public static class CapturedView<T extends RealType<T>> {
		CapturedView(final Img<T> image,
		             final AffineTransform3D globalToScreen, final int timepoint, final int mipmapLevel) {
			this.image = image;
			this.globalToScreen = globalToScreen;
			this.timepoint = timepoint;
			this.mipmapLevel = mipmapLevel;
		}

		private final Img<T> image;
		private final AffineTransform3D globalToScreen;
		private final int timepoint;
		private final int mipmapLevel;

		/** 2D image of exactly the size of the viewer canvas, pixel (x,y) is the screen pixel (x,y). */
		public Img<T> getImage() { return image; }
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
	 * source's resolution level that fits best the current zoom (or using the
	 * given zoom level), and using the selected interpolation.
	 * The returned object carries the change counter valid <i>before</i> the
	 * capturing started, so a change during the capturing is noticed by
	 * the {@link #hasChangedSince(long)}. For the output, every pixel
	 * value is passed through the {@code valueConverter} before it is stored. This is
	 * for subclasses that offer their own, converted, views.
	 *
	 * @param mipmapLevel the resolution level to read from, or -1 for the best-fitting one
	 * @param valueConverter applied on every pixel value, must not be null (use
	 *                       {@link DoubleUnaryOperator#identity()} for no conversion)
	 * @return the captured view; its image is zero-filled if the source is not present at the current timepoint
	 */
	public static <OT extends RealType<OT> & NativeType<OT>, IT extends RealType<IT>>
	CapturedView<OT> captureView(final Source<IT> source, final int tp,
	                             final AffineTransform3D globalToScreen,
	                             final int mipmapLevel, final Interpolation interpolation,
	                             final int outputWidth, final int outputHeight,
	                             final OT outputPixelType,
	                             final DoubleUnaryOperator valueConverter) {

		if (!source.isPresent(tp)) {
			return new CapturedView<>(getEmptyScreenPixels(outputWidth,outputHeight,outputPixelType), globalToScreen, tp, 0);
		}

		final int level = mipmapLevel >= 0 ? mipmapLevel
				: MipmapTransforms.getBestMipMapLevel(globalToScreen, source, tp);

		// we want: screen -> source pixel grid  ==  (globalToScreen * sourceToGlobal)^-1
		final AffineTransform3D sourceToGlobal = new AffineTransform3D();
		source.getSourceTransform(tp, level, sourceToGlobal);
		sourceToGlobal.preConcatenate(globalToScreen); //means: source -> Global -> Screen
		final AffineTransform3D screenToSource = sourceToGlobal.inverse();

		final Img<OT> img = collectScreenPixels(source.getSource(tp, level),
				screenToSource, interpolation, outputWidth, outputHeight, outputPixelType, valueConverter);
		return new CapturedView<>(img, globalToScreen, tp, level);
	}

	/**
	 * The workhorse: Resamples the source into a 2D image of the given screen size,
	 * such that pixel (x,y) of the output holds the value of the source at the global
	 * position that the viewer transform maps to the screen position (x,y,0).
	 * The pixel values are passed through the valueConverter.
	 *
	 * @param valueConverter applied on every pixel value, must not be null
	 * @throws IllegalStateException if the screen size is not positive (e.g. the viewer is not displayed yet)
	 */
	public static <OT extends RealType<OT> & NativeType<OT>, IT extends RealType<IT>> Img<OT> collectScreenPixels(
			final RandomAccessibleInterval<IT> srcImg,
			final AffineTransform3D screenToSrcImg,
			final Interpolation interpolation,
			final int outputWidth, final int outputHeight,
			final OT outputPixelType,
			final DoubleUnaryOperator valueConverter) {

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
			px.setReal( valueConverter.applyAsDouble( srcRealImgPtr.setPositionAndGet(srcImgPos).getRealDouble() ) );
		}

		return screenViewImg;
	}

	public static <OT extends RealType<OT> & NativeType<OT>> Img<OT> getEmptyScreenPixels(
			  final int outputWidth, final int outputHeight,
			  OT outputPixelType) {

		if (outputWidth <= 0 || outputHeight <= 0)
			throw new IllegalStateException("Viewer canvas has no size (" + outputWidth + "x" + outputHeight + ").");
		return new ArrayImgFactory<>(outputPixelType).create(outputWidth, outputHeight);
	}
}
