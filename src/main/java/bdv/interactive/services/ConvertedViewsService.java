package bdv.interactive.services;

import bdv.tools.brightness.ConverterSetup;
import bdv.util.BdvHandle;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

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
		setLastRange();
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
	private double lastMin;
	private double lastMax;
	private double lastRange;

	private void setLastRange() {
		//NB: guards only against a zero (or inverted) range, any positive range
		//    (including one narrower than 1, common with float data) is kept
		lastRange = lastMax > lastMin ? lastMax - lastMin : 1.0;
	}

	protected synchronized void setupParametersChanged(final ConverterSetup setup) {
		//TODO test instanceof Jakub LUT editor and make decisions based on his listeners...
		//else the code below for the "legacy" Converters:
		final double min = setup.getDisplayRangeMin();
		final double max = setup.getDisplayRangeMax();
		if (min == lastMin && max == lastMax) return; //e.g. only the color has changed
		lastMin = min;
		lastMax = max;
		setLastRange();
		notifyChange();
	}

	// ======================== capturing the views ========================
	/**
	 * Maps a pixel value through the bound source's current display range into [0,1], clamped.
	 * Applied only by the {@code getCurrentConvertedView()} methods; the inherited
	 * {@code getCurrentView()} methods return original values, as they do in the superclass.
	 */
	protected double convert(double in) {
		//TODO: use the source's actual converter
		double o = (in - lastMin) / lastRange;
		o = Math.min(1.0, Math.max(0.0, o)); //clamping...
		return o;
	}

	public <OT extends RealType<OT> & NativeType<OT>, IT extends RealType<IT> & NativeType<IT>>
	CapturedView<OT> getCurrentConvertedView(final OT outputPixelType) {
		return getCurrentConvertedView(outputPixelType, -1, Interpolation.NLINEAR);
	}

	public <OT extends RealType<OT> & NativeType<OT>, IT extends RealType<IT> & NativeType<IT>>
	CapturedView<OT> getCurrentConvertedView(final OT outputPixelType,
	                                         final int mipmapLevel, final Interpolation interpolation) {
		return captureView((Source)source.getSpimSource(), outputPixelType,
				mipmapLevel, interpolation, this::convert);
	}
}
