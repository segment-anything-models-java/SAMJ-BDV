package bdv.interactive.plans;

import bdv.interactive.services.BdvPromptsEvent;
import bdv.interactive.services.BdvPromptsService;
import bdv.viewer.ViewerPanel;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * Helpers for walking a plan (a {@code List<BdvPromptsEvent>}) in a BDV. Executing a plan is
 * left to the client, typically just:
 * <pre>
 *   for (BdvPromptsEvent item : plan) {
 *       PlanUtils.positionAndShowPromptAccordingToEvent(promptsService, item);
 *       myListener.onPromptEntered(item);
 *   }
 * </pre>
 * on whichever thread suits the client. Note that if this runs on the AWT Event Dispatch Thread,
 * the BDV is not repainted before the loop ends; run it on another thread to see every step.
 * Both methods are safe to call from any thread.
 */
public final class PlanUtils {
	private PlanUtils() {}

	/** Sets the BDV's timepoint and viewer transform to those of the event, if they differ. */
	public static void positionBdvAccordingToEvent(final ViewerPanel viewer, final BdvPromptsEvent event) {
		if (viewer.state().getCurrentTimepoint() != event.getTimepoint())
			viewer.state().setCurrentTimepoint(event.getTimepoint());

		final AffineTransform3D current = viewer.state().getViewerTransform();
		final AffineTransform3D wanted = event.getGlobalToScreenTransform();
		for (int r = 0; r < 3; ++r)
			for (int c = 0; c < 4; ++c)
				if (current.get(r, c) != wanted.get(r, c)) {
					//NB: setting it only when it differs doesn't needlessly bump the views services' change counters
					viewer.state().setViewerTransform(wanted);
					return;
				}
	}

	/** Sets the BDV's timepoint and viewer transform to those of the event, if they differ. */
	public static void positionBdvAccordingToEvent(final BdvPromptsService service, final BdvPromptsEvent event) {
		positionBdvAccordingToEvent(service.getBdvHandle().getViewerPanel(), event);
	}

	/**
	 * Positions the BDV (see {@link #positionBdvAccordingToEvent(ViewerPanel, BdvPromptsEvent)})
	 * and shows the event's box in the style of the event's action, replacing any box shown before.
	 * The caller is advised to consider using {@link BdvPromptsService#hideBox()} to hide the
	 * box/prompt, e.g., when this is operated in some non-interactive session.
	 */
	public static void positionAndShowPromptAccordingToEvent(final BdvPromptsService service, final BdvPromptsEvent event) {
		positionBdvAccordingToEvent(service.getBdvHandle().getViewerPanel(), event);
		service.showBox(event.getActionName(), event.getMinX(), event.getMinY(), event.getMaxX(), event.getMaxY());
	}
}
