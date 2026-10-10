package bdv.interactive.services;

import bdv.util.BdvHandle;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.ViewerState;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.function.DoubleUnaryOperator;

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
public class OriginalViewsService extends BdvPromptsViewService implements AutoCloseable {

	public OriginalViewsService(final BdvHandle bdv) {
		super(bdv);
		this.viewer.state().changeListeners().add(stateListener);
		this.viewer.getDisplayComponent().addComponentListener(resizeListener);
	}

	/** Unhooks all listeners from the BDV. */
	@Override
	public void close() {
		viewer.getDisplayComponent().removeComponentListener(resizeListener);
		viewer.state().changeListeners().remove(stateListener);
	}

	// ======================== change monitoring ========================
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
	 * @return the captured view; its image is zero-filled if the source is not present at the current timepoint
	 */
	public <OT extends RealType<OT> & NativeType<OT>, IT extends RealType<IT>>
	CapturedView<OT> getCurrentView(final Source<IT> source, final OT outputPixelType,
	                                final int mipmapLevel, final Interpolation interpolation) {

		final ViewerState state = viewer.state().snapshot();
		final int tp = state.getCurrentTimepoint();
		final int width = viewer.getDisplayComponent().getWidth();
		final int height = viewer.getDisplayComponent().getHeight();
		final AffineTransform3D globalToScreen = state.getViewerTransform();

		return captureView(source, tp, globalToScreen, mipmapLevel, interpolation,
				width, height, outputPixelType, DoubleUnaryOperator.identity());
	}

	public <OT extends RealType<OT> & NativeType<OT>, IT extends RealType<IT>>
	CapturedView<OT> getEventView(final Source<IT> source, final OT outputPixelType,
	                              final BdvPromptsEvent event, final Interpolation interpolation) {

		final int tp = event.getTimepoint();
		final int width = event.getCanvasWidth();
		final int height = event.getCanvasHeight();
		final AffineTransform3D globalToScreen = event.getGlobalToScreenTransform();

		return captureView(source, tp, globalToScreen, -1, interpolation,
				width, height, outputPixelType, DoubleUnaryOperator.identity());
	}
}
