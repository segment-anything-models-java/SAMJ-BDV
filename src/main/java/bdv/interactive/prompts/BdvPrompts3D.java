package bdv.interactive.prompts;

import bdv.interactive.prompts.views.SlicingViews;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.RealType;

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
		int sx,sy, ex,ey;  //prompt corners
		int cx,cy;         //reference of the tracking centre of this slice -- tracking
		                   //centre on the follow-up slice tells how much to move the prompt
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
		//to make prompt-line no longer visible/rendered, we need to set samjOverlay.isLineReadyForDrawing = false;
		//this calls does =false as a side effect (and the call itself is innocent)
		samjOverlay.setStartOfLine(0,0);
	}

	public <LT extends RealType<LT>> void setupSlicing(RandomAccessible<LT> labelImage, LT labelToFollowAndFill,
	                                                   final int sx, final int sy, final int ex, final int ey) {
		slicer.resetView(viewerPanel);
		slicingParams.clear();
		//TODO:
		// go forward with the slicer as long as labelToFollow is found in labelImage within the current rectangle
		// at each step/position/slice: calculate geometric centre of the found labelToFollow and update the prompt

		//for now, hardcoded ten steps:
		SlicingStep currStep = new SlicingStep();
		currStep.offset = 0.0;
		currStep.cx = currStep.cy = 0;
		currStep.sx = sx; currStep.sy = sy;
		currStep.ex = ex; currStep.ey = ey;
		slicingParams.add(currStep);

		for (int i = 1; i < 10; ++i) {
			SlicingStep nextStep = new SlicingStep();
			nextStep.offset = i;             //shortcut for: currStep.offset + 1.0
			nextStep.cx = nextStep.cy = i*5; //fake shift of the tracking centre

			nextStep.sx = currStep.sx + nextStep.cx-currStep.cx; nextStep.sy = currStep.sy + nextStep.cy-currStep.cy;
			nextStep.ex = currStep.ex + nextStep.cx-currStep.cx; nextStep.ey = currStep.ey + nextStep.cy-currStep.cy;
			slicingParams.add(nextStep);
			currStep = nextStep;
		}
	}
}
