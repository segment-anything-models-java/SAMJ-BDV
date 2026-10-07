package bdv.interactive.services;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Immutable description of a finished rubber-band box.
 * <p>
 * The box is given in <b>screen (viewer canvas) pixel coordinates</b>, normalized
 * such that {@code min <= max}, clamped into the canvas, and with both ends
 * inclusive. Together with the box, the event carries a copy of the viewer
 * transform and the timepoint that were in effect when the dragging ended,
 * so that the box can be mapped into the global coordinates even if the view
 * has changed meanwhile.
 */
public final class RubberBandEvent {
	private final String actionName;
	private final int minX, minY, maxX, maxY;
	private final int canvasWidth, canvasHeight;
	private final AffineTransform3D globalToScreen;
	private final int timepoint;

	RubberBandEvent(final String actionName,
	                final int minX, final int minY, final int maxX, final int maxY,
	                final int canvasWidth, final int canvasHeight,
	                final AffineTransform3D globalToScreen, final int timepoint) {
		this.actionName = actionName;
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		this.canvasWidth = canvasWidth;
		this.canvasHeight = canvasHeight;
		this.globalToScreen = globalToScreen.copy();
		this.timepoint = timepoint;
	}

	/** Name of the action (drag behaviour) with which this box was dragged. */
	public String getActionName() { return actionName; }

	public int getMinX() { return minX; }
	public int getMinY() { return minY; }
	/** Inclusive. */
	public int getMaxX() { return maxX; }
	/** Inclusive. */
	public int getMaxY() { return maxY; }
	public int getWidth()  { return maxX - minX + 1; }
	public int getHeight() { return maxY - minY + 1; }

	/** The box as a 2D interval in the screen pixel coordinates. */
	public Interval asInterval() {
		return new FinalInterval(new long[] {minX, minY}, new long[] {maxX, maxY});
	}

	/** The box as {x_min, y_min, x_max, y_max}, ends inclusive. */
	public int[] asXYXY() {
		return new int[] {minX, minY, maxX, maxY};
	}

	/** Size of the viewer canvas at the time of the event. */
	public int getCanvasWidth()  { return canvasWidth; }
	public int getCanvasHeight() { return canvasHeight; }

	/** A copy of the viewer transform (global -&gt; screen) at the time of the event. */
	public AffineTransform3D getGlobalToScreenTransform() { return globalToScreen.copy(); }

	/** A copy of the inverse of the viewer transform (screen -&gt; global) at the time of the event. */
	public AffineTransform3D getScreenToGlobalTransform() { return globalToScreen.inverse(); }

	/** The viewer's current timepoint at the time of the event. */
	public int getTimepoint() { return timepoint; }

	@Override
	public String toString() {
		return "RubberBandEvent[" + actionName + ": " + minX + "," + minY + " -> " + maxX + "," + maxY
				+ " (" + getWidth() + "x" + getHeight() + "), tp=" + timepoint + "]";
	}
}
