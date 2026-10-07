package bdv.interactive.services;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import java.util.function.DoubleUnaryOperator;

/**
 * Like the {@link OriginalViewsService}, but bound to one particular source, and
 * additionally watching this source's {@link ConverterSetup}: a change of its
 * display range (the "contrast setting") also increments the change counter.
 * <p>
 * The {@link #getCurrentConvertedView(RealType)} returns the current view of the
 * bound source with its pixel values mapped through the display range into
 * [0,1] (or another requested range), clamped. That is, what the user sees in
 * terms of brightness, but not in terms of color.
 * <p>
 * Only changes of the display range are counted. Changes of the color only
 * (which do not influence the converted view) are ignored on purpose, to avoid
 * needless re-captures; see {@link #setupParametersChanged(ConverterSetup)}.
 * <p>
 * The change counter is shared with the views monitoring, so
 * {@link #hasChangedSince(long)} reports either kind of change.
 */
public class ConvertedViewsService extends OriginalViewsService {

	/** The {@link ConverterSetup} is looked up from the BDV's {@link BdvHandle#getConverterSetups()}. */
	public ConvertedViewsService(final BdvHandle bdv, final SourceAndConverter<? extends RealType<?>> source) {
		this(bdv, source, lookupConverterSetup(bdv, source));
	}

	public ConvertedViewsService(final BdvHandle bdv,
	                             final SourceAndConverter<? extends RealType<?>> source,
	                             final ConverterSetup converterSetup) {
		super(bdv);
		this.source = source;
		this.converterSetup = converterSetup;
		this.lastMin = converterSetup.getDisplayRangeMin();
		this.lastMax = converterSetup.getDisplayRangeMax();
		converterSetup.setupChangeListeners().add(setupListener);
	}

	private static ConverterSetup lookupConverterSetup(final BdvHandle bdv, final SourceAndConverter<?> source) {
		final ConverterSetup cs = bdv.getConverterSetups().getConverterSetup(source);
		if (cs == null)
			throw new IllegalArgumentException("No ConverterSetup found for source '"
					+ source.getSpimSource().getName() + "', is the source shown in this BDV?");
		return cs;
	}

	private final SourceAndConverter<? extends RealType<?>> source;
	private final ConverterSetup converterSetup;

	public SourceAndConverter<? extends RealType<?>> getSource() { return source; }
	public ConverterSetup getConverterSetup() { return converterSetup; }

	@Override
	public void close() {
		converterSetup.setupChangeListeners().remove(setupListener);
		super.close();
	}

	// ======================== change monitoring ========================
	private final ConverterSetup.SetupChangeListener setupListener = this::setupParametersChanged;
	private double lastMin, lastMax;

	protected synchronized void setupParametersChanged(final ConverterSetup setup) {
		final double min = setup.getDisplayRangeMin();
		final double max = setup.getDisplayRangeMax();
		if (min == lastMin && max == lastMax) return; //e.g. only the color has changed
		lastMin = min;
		lastMax = max;
		notifyChange();
	}

	// ======================== capturing the views ========================
	/** The current view of the bound source, with the values mapped through the display range into [0,1]. */
	public <O extends RealType<O> & NativeType<O>>
	CapturedView<O> getCurrentConvertedView(final O outputPixelType) {
		return getCurrentConvertedView(outputPixelType, 0.0, 1.0);
	}

	/**
	 * The current view of the bound source, with the display range [min,max]
	 * mapped linearly onto [targetMin,targetMax], values outside are clamped.
	 */
	public <O extends RealType<O> & NativeType<O>>
	CapturedView<O> getCurrentConvertedView(final O outputPixelType, final double targetMin, final double targetMax) {
		//NB: the counter must be read before the display range, otherwise a change of
		//    the range in between would go unnoticed; getCurrentView() reads the counter
		//    again (later), which is only more conservative
		final long counter = getChangeCounter();
		final DoubleUnaryOperator mapper = displayRangeMapper(
				converterSetup.getDisplayRangeMin(), converterSetup.getDisplayRangeMax(), targetMin, targetMax);
		final CapturedView<O> view = getCurrentView(getBoundSpimSource(), outputPixelType, -1, Interpolation.NLINEAR, mapper);
		return new CapturedView<>(view.getImage(), counter,
				view.getGlobalToScreenTransform(), view.getTimepoint(), view.getMipmapLevel());
	}

	/** The current view of the bound source with the original pixel values. */
	public <O extends RealType<O> & NativeType<O>>
	CapturedView<O> getCurrentOriginalView(final O outputPixelType) {
		return getCurrentView(getBoundSpimSource(), outputPixelType);
	}

	private Source<? extends RealType<?>> getBoundSpimSource() {
		return source.getSpimSource();
	}

	public static DoubleUnaryOperator displayRangeMapper(final double min, double max,
	                                                      final double targetMin, final double targetMax) {
		if (max == min) max = min + 1.0;
		final double scale = (targetMax - targetMin) / (max - min);
		final double lo = Math.min(targetMin, targetMax);
		final double hi = Math.max(targetMin, targetMax);
		return v -> Math.min(Math.max((v - min) * scale + targetMin, lo), hi);
	}
}
