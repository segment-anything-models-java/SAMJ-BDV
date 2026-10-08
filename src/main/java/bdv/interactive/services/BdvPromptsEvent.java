package bdv.interactive.services;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Immutable description of a finished, just entered rectangular prompt (a box).
 * <p>
 * The box is given in <b>screen (viewer canvas) pixel coordinates</b>, normalized
 * such that {@code min <= max}, clamped into the canvas, and with both ends
 * inclusive. Together with the box, the event carries a copy of the viewer
 * transform and the timepoint that were in effect when the dragging ended,
 * so that the box can be mapped into the global coordinates even if the view
 * has changed meanwhile.
 */
public final class BdvPromptsEvent {
	private final String actionName;
	private final int minX, minY, maxX, maxY;
	private final int canvasWidth, canvasHeight;
	private final AffineTransform3D globalToScreen;
	private final int timepoint;

	BdvPromptsEvent(final String actionName,
	                final int minX, final int minY, final int maxX, final int maxY,
	                final int canvasWidth, final int canvasHeight,
	                final AffineTransform3D globalToScreen, final int timepoint) {
		// "author" of the event
		this.actionName = actionName;

		// which prompt did the author created
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;

		// what we were looking at expressed as the configuration, not as
		// the content itself (this would need to be fetched, e.g., from viewServices)
		//
		// the window size
		this.canvasWidth = canvasWidth;
		this.canvasHeight = canvasHeight;
		//
		// the spatial and temporal "view configuration"
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

	// ======================== derived events ========================
	/**
	 * The same prompt (action, box, canvas size, timepoint) under another view, e.g.
	 * the next slice. The box keeps its screen coordinates.
	 */
	public BdvPromptsEvent withView(final AffineTransform3D newGlobalToScreen) {
		return new BdvPromptsEvent(actionName, minX, minY, maxX, maxY,
				canvasWidth, canvasHeight, newGlobalToScreen, timepoint);
	}

	/**
	 * The same view (and action) with another box, given in screen coordinates.
	 * The box is normalized (min &lt;= max) and clamped into the canvas.
	 */
	public BdvPromptsEvent withBox(final int x0, final int y0, final int x1, final int y1) {
		final int maxX = Math.max(canvasWidth - 1, 0), maxY = Math.max(canvasHeight - 1, 0);
		return new BdvPromptsEvent(actionName,
				clamp(Math.min(x0, x1), maxX), clamp(Math.min(y0, y1), maxY),
				clamp(Math.max(x0, x1), maxX), clamp(Math.max(y0, y1), maxY),
				canvasWidth, canvasHeight, globalToScreen, timepoint);
	}

	/**
	 * True if both events were (or would be) entered on exactly the same view: the same viewer
	 * transform, timepoint and canvas size. Their view images, as obtained from a views service,
	 * are then the same, provided the data and the display settings didn't change meanwhile.
	 * The boxes and action names are not compared.
	 */
	public boolean hasSameViewAs(final BdvPromptsEvent other) {
		if (other == null) return false;
		if (this == other) return true;
		if (timepoint != other.timepoint || canvasWidth != other.canvasWidth || canvasHeight != other.canvasHeight)
			return false;
		for (int r = 0; r < 3; ++r)
			for (int c = 0; c < 4; ++c)
				if (globalToScreen.get(r, c) != other.globalToScreen.get(r, c)) return false;
		return true;
	}

	private static int clamp(final int v, final int max) {
		return Math.min(Math.max(v, 0), max);
	}

	@Override
	public String toString() {
		return "BdvPromptEvent[" + actionName + ": " + minX + "," + minY + " -> " + maxX + "," + maxY
				+ " (" + getWidth() + "x" + getHeight() + "), tp=" + timepoint + "]";
	}
}
