package ai.nets.samj.bdv.util;

import bdv.util.BdvHandle;
import bdv.viewer.SynchronizedViewerState;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * This class holds a particular viewport (spatial) at a particular time
 * point (temporal) at some data in some BDV. That said, it is not bound
 * to any particular BDV instance. However, a particular instance is used
 * to read-out from it the current viewport and time point.
 */
public class SpatioTemporalView {
	public SpatioTemporalView(BdvHandle bdv) {
		SynchronizedViewerState state = bdv.getViewerPanel().state();
		state.getViewerTransform(this.viewport);
		timepoint = state.getCurrentTimepoint();
	}

	private final AffineTransform3D viewport = new AffineTransform3D();
	private final int timepoint;

	public void applyOnThis(BdvHandle bdv) {
		SynchronizedViewerState state = bdv.getViewerPanel().state();
		state.setCurrentTimepoint(this.timepoint);
		state.setViewerTransform(this.viewport);
	}
}