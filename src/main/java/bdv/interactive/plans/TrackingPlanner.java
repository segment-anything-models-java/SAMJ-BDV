package bdv.interactive.plans;

import bdv.interactive.prompts.views.SlicingViews;
import bdv.interactive.services.BdvPromptsEvent;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans for the tracked prompt ("K"): starting on the view of the user's box, it walks slice by
 * slice along the viewing direction (see {@link SlicingViews}) for as long as a
 * {@link LabelPresenceIndicator} reports the object inside the box, and moves and resizes the box
 * along with the object. The plan has one item per slice; its first item is the user's event itself.
 * <p>
 * The tracking itself: on every slice, the bounding box of all "present" pixels inside the
 * previous slice's box is computed; the box for that slice is the previous box shifted by how
 * much the bounding box has moved, and resized by how much it has grown or shrunk. The walk
 * stops at the first slice without any present pixel in the box, or after {@code maxSlices}.
 * <p>
 * The whole plan is computed upfront (so that it can be reviewed before it is executed),
 * from what the indicator reports at that moment.
 */
public final class TrackingPlanner {
	private TrackingPlanner() {}

	/** Tells if the tracked object is present at a position given in global coordinates. */
	public interface LabelPresenceIndicator {
		/** Called once before every slice is examined, e.g. to refresh some caches. */
		default void prepareForQueryingSession() {}

		boolean isPresent(final RealLocalizable globalPosition);
	}

	/**
	 * @param initialEvent initial user's box, and the view it was drawn on; tracking starts from here
	 * @param step signed slicing step in global units; positive goes further away from the viewer
	 * @param maxSlices upper limit on the number of plan items (including the first one)
	 * @return the plan; empty if the object is not present in the user's box already
	 */
	public static List<BdvPromptsEvent> plan(final BdvPromptsEvent initialEvent,
	                                         final LabelPresenceIndicator indicator,
	                                         final double step, final int maxSlices) {
		final List<BdvPromptsEvent> plan = new ArrayList<>();
		final SlicingViews slicer = new SlicingViews(initialEvent.getGlobalToScreenTransform());
		indicator.prepareForQueryingSession();

		BdvPromptsEvent prevEvent = initialEvent;
		int[] prevBBox = null;

		for (int slice = 0; slice < maxSlices; ++slice) {
			final BdvPromptsEvent examinedEvent = slice == 0 ? initialEvent
					: prevEvent.withView( slicer.sameViewShiftedBy(slice * step) );

			final int[] foundBBox = findLabelBBoxOrNull(indicator, examinedEvent.getScreenToGlobalTransform(), prevEvent.asXYXY());
			if (foundBBox == null) {
				System.out.println("Found NOT the label.");
				break;
			}
			System.out.println("Found label at screen offset " + slice
					+ ": BBox "+printBBox(foundBBox)+" in screen pixel coords");

			if (slice > 0) {
				//move and resize the box by how much the label's bounding box has changed
				final int[] box = prevEvent.asXYXY();
				prevEvent = examinedEvent.withBox(
						box[0] + foundBBox[0] - prevBBox[0],
						box[1] + foundBBox[1] - prevBBox[1],
						box[2] + foundBBox[2] - prevBBox[2],
						box[3] + foundBBox[3] - prevBBox[3]);
			}
			prevBBox = foundBBox;
			plan.add(prevEvent);
		}
		return plan;
	}

	/** @return bounding box {x0,y0,x1,y1} of present pixels within the screen box, or null if none */
	static int[] findLabelBBoxOrNull(final LabelPresenceIndicator indicator,
	                                 final AffineTransform3D screenToGlobal,
	                                 final int[] screenBoxAsXYXY) {

		final RealPoint pos = new RealPoint(3);
		final double[] screenPos = new double[3];
		int[] bboxAsXYXY = null;

		for (int y = screenBoxAsXYXY[1]; y <= screenBoxAsXYXY[3]; ++y) {
			screenPos[1] = y;
			for (int x = screenBoxAsXYXY[0]; x <= screenBoxAsXYXY[2]; ++x) {
				screenPos[0] = x;
				pos.setPosition(screenPos);
				screenToGlobal.apply(pos, pos);
				if (!indicator.isPresent(pos)) continue;

				if (bboxAsXYXY == null) bboxAsXYXY = new int[] {x, y, x, y};
				else {
					bboxAsXYXY[0] = Math.min(x, bboxAsXYXY[0]);
					bboxAsXYXY[1] = Math.min(y, bboxAsXYXY[1]);
					bboxAsXYXY[2] = Math.max(x, bboxAsXYXY[2]);
					bboxAsXYXY[3] = Math.max(y, bboxAsXYXY[3]);
				}
			}
		}
		return bboxAsXYXY;
	}

	static String printBBox(final int[] xyxy) {
		return "[" + xyxy[0] + "," + xyxy[1] + " -> " + xyxy[2] + "," + xyxy[3] + "]";
	}
}
