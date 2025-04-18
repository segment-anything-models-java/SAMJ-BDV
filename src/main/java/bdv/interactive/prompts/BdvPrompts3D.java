package bdv.interactive.prompts;

import bdv.interactive.prompts.views.SlicingViews;
import bdv.viewer.ViewerPanel;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;

import java.awt.Dimension;
import java.util.LinkedList;
import java.util.List;

public class BdvPrompts3D implements Runnable {
	public BdvPrompts3D(final ViewerPanel viewerPanel,
	                    final BdvPrompts.PromptsAndPolygonsDrawingOverlay samjOverlay,
	                    final Runnable callbackOnNewPromptPosition) {

		this.viewerPanel = viewerPanel;
		this.samjOverlay = samjOverlay;
		this.callbackOnNewPromptPosition = callbackOnNewPromptPosition;
		this.slicer = new SlicingViews(viewerPanel);
	}

	private final ViewerPanel viewerPanel;
	private final BdvPrompts.PromptsAndPolygonsDrawingOverlay samjOverlay;
	private final Runnable callbackOnNewPromptPosition;


	private final SlicingViews slicer;
	private final List<SlicingStep> slicingParams = new LinkedList<>();

	static class SlicingStep {
		double offset;     //offset of this slice
		int sx,sy, ex,ey;  //prompt corners for this slice
		int[] xyxy;        //reference of the tracking bounding box of this slice,
		                   //difference to such bounding box in the follow-up slice tells
		                   //how much to modify (move, resize) the prompt
	}

	/**
	 * Follows the 'slicingParams' and at each step it:
	 *   - updates 'samjOverlay' with the current prompt position
	 *   - configures the 'slicer' - the BDV view
	 *   - re-renders if needed
	 *   - calls 'callbackOnNewPromptPosition' to do what it needs at the current setting (which
	 *     is most likely going to be: update progress bar and call BdvPrompt's prompt processing)
	 */
	@Override
	public void run() {
		for (SlicingStep s : this.slicingParams) {
			samjOverlay.setStartOfLine(s.sx, s.sy);
			samjOverlay.setEndOfLine(s.ex, s.ey);
			viewerPanel.state().setViewerTransform( slicer.sameViewShiftedBy(s.offset) );
			callbackOnNewPromptPosition.run();
		}
		//the slicing is over, hide the prompt again
		samjOverlay.requestNoDrawingOfPromptLine();
		viewerPanel.getDisplayComponent().repaint();
	}

	public int setupSlicing(boolean stepAway, final int sx, final int sy, final int ex, final int ey) {
		slicingParams.clear();
		slicer.resetView(viewerPanel);
		SlicingStep currStep = new SlicingStep();
		currStep.offset = stepAway ? 1 : -1;
		currStep.sx = sx; currStep.sy = sy;
		currStep.ex = ex; currStep.ey = ey;
		//currStep.xyxy intentionally avoided 'cause it is not needed in run() above
		slicingParams.add(currStep);
		return 1;
	}

	public int setupSlicing(final LabelPresenceIndicatorAtGlobalCoord labelPresenceIndicatorAtGlobalCoord,
	                        final int sx, final int sy, final int ex, final int ey) {
		//plan:
		// iterate forward with the 'slicer' as long as 'labelPresenceIndicatorAtGlobalCoord()' indicates presence
		// of any tracked pixel in the current slice (at coordinates that we here sweep over and challenge
		// the indicator) within the current rectangle prompt [sx,sy,ex,ey], and at each step/position/slice:
		// calculate bounding box around the found/indicated positions, and update the prompt accordingly

		slicer.resetView(viewerPanel);
		AffineTransform3D globalToScreenT = viewerPanel.state().getViewerTransform();
		viewerPanel.getDisplayComponent().getSize(screenPixelSize);
		screenPixelSize.width -= 1;  //to make it the last valid coordinate (rather than 'size' itself)
		screenPixelSize.height -= 1;

		labelPresenceIndicatorAtGlobalCoord.prepareForQueryingSession();

		int[] bbox_xyxy = new int[4];
		boolean foundLabel
			= calculateLabelBBox(labelPresenceIndicatorAtGlobalCoord, globalToScreenT.inverse(), sx,sy, ex,ey, bbox_xyxy);

		if (foundLabel) System.out.println("Found label at screen! Bbox "+print_bbox(bbox_xyxy)+" in screen pixel coords");
		else System.out.println("Found NOT the label!");

		slicingParams.clear();
		if (!foundLabel) return 0;

		int offset = 0;
		SlicingStep currStep = new SlicingStep();
		currStep.offset = offset;
		currStep.xyxy = bbox_xyxy;
		currStep.sx = sx; currStep.sy = sy;
		currStep.ex = ex; currStep.ey = ey;
		slicingParams.add(currStep);

		while (foundLabel) {
			globalToScreenT = slicer.sameViewShiftedBy(++offset);
			bbox_xyxy = new int[4];
			foundLabel = calculateLabelBBox(labelPresenceIndicatorAtGlobalCoord, globalToScreenT.inverse(),
			                                currStep.sx,currStep.sy, currStep.ex,currStep.ey, bbox_xyxy);

			if (foundLabel) System.out.println("Found label at offset "+offset+" in the Bbox "+print_bbox(bbox_xyxy)+" in screen pixel coords");
			else System.out.println("Found NOT the label at offset "+offset+", stopping.");

			if (foundLabel) {
				SlicingStep nextStep = new SlicingStep();
				nextStep.offset = offset; //shortcut for: currStep.offset + 1.0
				nextStep.xyxy = bbox_xyxy;
				nextStep.sx = Math.max(currStep.sx + nextStep.xyxy[0]-currStep.xyxy[0], 0);
				nextStep.sy = Math.max(currStep.sy + nextStep.xyxy[1]-currStep.xyxy[1], 0);
				nextStep.ex = Math.min(currStep.ex + nextStep.xyxy[2]-currStep.xyxy[2], screenPixelSize.width);
				nextStep.ey = Math.min(currStep.ey + nextStep.xyxy[3]-currStep.xyxy[3], screenPixelSize.height);
				slicingParams.add(nextStep);
				currStep = nextStep;
			}
		}

		System.out.println("Tracking slices finished, now re-iterate them with the actual prompting....");
		return offset;
	}


	public interface LabelPresenceIndicatorAtGlobalCoord {
		void prepareForQueryingSession();
		boolean isPresent(final RealLocalizable position);
	}

	boolean calculateLabelCentre(final LabelPresenceIndicatorAtGlobalCoord labelPresenceIndicatorAtGlobalCoord,
	                             final AffineTransform3D screenToGlobalT,
	                             final int sx, final int sy, final int ex, final int ey,
	                             final int[] out_cxcy) {
		long sum_cx = 0;
		long sum_cy = 0;
		long sum_cnt = 0;

		screenCoord[2] = 0;
		for (int y = sy; y <= ey; ++y) {
			screenCoord[1] = y;
			for (int x = sx; x <= ex; ++x) {
				screenCoord[0] = x;

				realPtr.setPosition(screenCoord);
				screenToGlobalT.apply(realPtr, realPtr);
				if (labelPresenceIndicatorAtGlobalCoord.isPresent(realPtr)) {
					sum_cx += x;
					sum_cy += y;
					sum_cnt++;
				}
			}
		}

		if (sum_cnt == 0) return false;

		out_cxcy[0] = (int)(sum_cx / sum_cnt);
		out_cxcy[1] = (int)(sum_cy / sum_cnt);
		return true;
	}

	boolean calculateLabelBBox(final LabelPresenceIndicatorAtGlobalCoord labelPresenceIndicatorAtGlobalCoord,
	                           final AffineTransform3D screenToGlobalT,
	                           final int sx, final int sy, final int ex, final int ey,
	                           final int[] out_xyxy) {
		long sum_cnt = 0;

		screenCoord[2] = 0;
		for (int y = sy; y <= ey; ++y) {
			screenCoord[1] = y;
			for (int x = sx; x <= ex; ++x) {
				screenCoord[0] = x;

				realPtr.setPosition(screenCoord);
				screenToGlobalT.apply(realPtr, realPtr);
				if (labelPresenceIndicatorAtGlobalCoord.isPresent(realPtr)) {
					sum_cnt++;
					if (sum_cnt == 1) {
						//initiate the bbox
						out_xyxy[0] = x;
						out_xyxy[1] = y;
						out_xyxy[2] = x;
						out_xyxy[3] = y;
					} else {
						//possibly grow the bbox
						out_xyxy[0] = Math.min(x, out_xyxy[0]);
						out_xyxy[1] = Math.min(y, out_xyxy[1]);
						out_xyxy[2] = Math.max(x, out_xyxy[2]);
						out_xyxy[3] = Math.max(y, out_xyxy[3]);
					}
				}
			}
		}

		return sum_cnt > 0;
	}

	String print_bbox(final int[] xyxy) {
		return "["+xyxy[0]+","+xyxy[1]+" -> "+xyxy[2]+","+xyxy[3]+"]";
	}

	final private int[] screenCoord = new int[3];
	final private RealPoint realPtr = new RealPoint(3);
	final private Dimension screenPixelSize = new Dimension();
}
