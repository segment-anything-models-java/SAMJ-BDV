package bdv.interactive.behaviours;

import bdv.KeyConfigScopes;
import bdv.interactive.services.BdvPromptsService;
import bdv.interactive.views.SideViews;
import bdv.interactive.views.SlicingViews;
import bdv.viewer.ViewerPanel;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;

import java.awt.*;

/**
 * The single place where the actions around BDV prompting are named, given their default
 * triggers and their descriptions, and from where they are installed into a {@link BdvPromptsService}.
 * <ul>
 *   <li><b>Prompts</b>: drag a box (L, shift L, J, K) or repeat the last one (ctrl L),
 *       see {@link #addPromptActionsTo(BdvPromptsService)}. What happens with the prompts
 *       is up to the listeners the client registers for these actions.</li>
 *   <li><b>Side views</b> ({@link SideViews}): animated 90-degree rotations around the current
 *       <i>screen</i> x- or y-axis (through the mouse position), and back to the view from before
 *       the rotation. Unlike BDV's "align XY/ZY/XZ plane", which snap onto the <i>data</i> axes,
 *       these give views perpendicular to any oblique cut.</li>
 *   <li><b>Slicing</b> ({@link SlicingViews}): moves the view along its viewing direction by a
 *       fixed step in <i>global</i> units, regardless of zoom. Unlike BDV's "forward z"/"backward z",
 *       whose step is one <i>screen</i> unit and thus depends on the zoom.</li>
 * </ul>
 * All actions are keymap-driven (see {@link BdvPromptsService#addAction}), and can be removed
 * with {@link BdvPromptsService#removeAction(String)}. The {@link Descriptions} provider makes
 * them listed in the BDV keymap editor: BDV's {@code KeymapManager} harvests all
 * {@link CommandDescriptionProvider}s of the scope {@link KeyConfigScopes#BIGDATAVIEWER} found
 * on the classpath (through the SciJava plugin index, which is generated at compile time from
 * the {@link Plugin} annotation; no {@code Context} is needed in our code).
 */
public final class BdvPromptsBehaviours {
	private BdvPromptsBehaviours() {}

	// ======================== prompts ========================
	public static final String PROMPT = "rectangle prompt on original view";
	public static final String[] PROMPT_KEYS = new String[] { "L" };

	public static final String PROMPT_CONTRAST = "rectangle prompt on contrast-adjusted view";
	public static final String[] PROMPT_CONTRAST_KEYS = new String[] { "shift L" };

	public static final String REPEAT_PROMPT_NEARER_SLICE = "repeat prompt on nearer slice on original view";
	public static final String[] REPEAT_PROMPT_NEARER_SLICE_KEYS = new String[] { "shift N" };

	public static final String REPEAT_PROMPT_FURTHER_SLICE = "repeat prompt on further slice on original view";
	public static final String[] REPEAT_PROMPT_FURTHER_SLICE_KEYS = new String[] { "shift M" };

	public static final String REPEAT_PROMPT_HERE = "repeat the same prompt on original view";
	public static final String[] REPEAT_PROMPT_HERE_KEYS = new String[] { "ctrl L" };

	public static final String MULTI_PROMPT = "rectangle multi-prompt on original view";
	public static final String[] MULTI_PROMPT_KEYS = new String[] { "J" };

	public static final String TRACKING_PROMPT = "prompt tracked through slices on original view";
	public static final String[] TRACKING_PROMPT_KEYS = new String[] { "K" };

	public static final String TRACKING_PROMPT_CONTRAST = "prompt tracked through slices on contrast-adjusted view";
	public static final String[] TRACKING_PROMPT_CONTRAST_KEYS = new String[] { "shift K" };

	/** Adds all the prompt actions, with their default triggers, to the service. */
	public static void addPromptActionsTo(final BdvPromptsService service) {
		addPromptActionsTo(service, 1.0);
	}

	/**
	 * Adds all the prompt actions, with their default triggers, to the service.
	 *
	 * @param slicingStep the step of the slicing actions, in global units (the units shown in BDV's
	 *                    top-right corner); 1.0 is one pixel for data with unit pixel size, for
	 *                    calibrated data use, e.g., the voxel size along the typical viewing direction
	 */
	public static void addPromptActionsTo(final BdvPromptsService service, final double slicingStep) {
		service.addInsertPromptAction(PROMPT, PROMPT_KEYS);
		service.addInsertPromptAction(PROMPT_CONTRAST, PROMPT_CONTRAST_KEYS);
		service.addInsertPromptAction(MULTI_PROMPT, MULTI_PROMPT_KEYS);
		service.addRepeatPromptAction(() -> {}, REPEAT_PROMPT_HERE, REPEAT_PROMPT_HERE_KEYS);
		service.setBoxStyle(PROMPT_CONTRAST, BdvPromptsService.LineStyle.DASHED, BdvPromptsService.DEFAULT_LINE_THICKNESS);
		service.setBoxColor(MULTI_PROMPT, Color.YELLOW);

		if (is2D(service)) return;
		if (slicingStep <= 0)
			throw new IllegalArgumentException("Slicing step must be positive, got " + slicingStep);

		service.addInsertPromptAction(TRACKING_PROMPT, TRACKING_PROMPT_KEYS);
		service.addInsertPromptAction(TRACKING_PROMPT_CONTRAST, TRACKING_PROMPT_CONTRAST_KEYS);
		service.setBoxStyle(TRACKING_PROMPT_CONTRAST, BdvPromptsService.LineStyle.DASHED, BdvPromptsService.DEFAULT_LINE_THICKNESS);
		service.setBoxColor(TRACKING_PROMPT, Color.BLUE);

		final ViewerPanel viewer = service.getBdvHandle().getViewerPanel();
		final SlicingViews sv = new SlicingViews(viewer);

		service.addRepeatPromptAction(() -> {
				sv.resetView(viewer);
				viewer.state().setViewerTransform(sv.sameViewShiftedBy(-slicingStep));
			}, REPEAT_PROMPT_NEARER_SLICE, REPEAT_PROMPT_NEARER_SLICE_KEYS);
		service.addRepeatPromptAction(() -> {
				sv.resetView(viewer);
				viewer.state().setViewerTransform(sv.sameViewShiftedBy(+slicingStep));
			}, REPEAT_PROMPT_FURTHER_SLICE, REPEAT_PROMPT_FURTHER_SLICE_KEYS);
	}

	// ======================== view navigation ========================
	public static final String FRONT_VIEW = "side views: rotate to front view";
	public static final String[] FRONT_VIEW_KEYS = new String[] { "ctrl I" };

	public static final String SIDE_VIEW = "side views: rotate to side view";
	public static final String[] SIDE_VIEW_KEYS = new String[] { "ctrl J" };

	public static final String REFERENCE_VIEW = "side views: rotate back to reference view";
	public static final String[] REFERENCE_VIEW_KEYS = new String[] { "ctrl K" };

	public static final String SLICE_NEARER = "slicing views: move one step closer";
	public static final String[] SLICE_NEARER_KEYS = new String[] { "ctrl N" };

	public static final String SLICE_FURTHER = "slicing views: move one step further";
	public static final String[] SLICE_FURTHER_KEYS = new String[] { "ctrl M" };

	/** @return true if the service's BDV is a 2D one, in which case no navigation is installed. */
	public static boolean is2D(final BdvPromptsService service) {
		return service.getBdvHandle().getViewerPanel().getOptionValues().is2D();
	}

	/**
	 * Installs the side views and the slicing actions; the slicing step is 1.0 global unit,
	 * the rotations take 1000 milliseconds.
	 *
	 * @return false (and nothing installed) if this is a 2D BDV
	 */
	public static boolean addNavigationActionsTo(final BdvPromptsService service) {
		return addNavigationActionsTo(service, 1.0, 1000);
	}

	/**
	 * Installs the {@link #FRONT_VIEW}, {@link #SIDE_VIEW} and {@link #REFERENCE_VIEW} actions
	 * (the front and side views rotate away from the <i>current</i> view, which becomes the
	 * reference view to which {@link #REFERENCE_VIEW} returns), and the {@link #SLICE_NEARER}
	 * and {@link #SLICE_FURTHER} actions.
	 *
	 * @param slicingStep the step of the slicing actions, in global units (the units shown in BDV's
	 *                    top-right corner); 1.0 is one pixel for data with unit pixel size, for
	 *                    calibrated data use, e.g., the voxel size along the typical viewing direction
	 * @param rotationMillis duration of the side views rotations, in milliseconds
	 * @return false (and nothing installed) if this is a 2D BDV
	 */
	public static boolean addNavigationActionsTo(final BdvPromptsService service,
	                                             final double slicingStep, final int rotationMillis) {
		if (is2D(service)) return false;
		if (slicingStep <= 0)
			throw new IllegalArgumentException("Slicing step must be positive, got " + slicingStep);

		final ViewerPanel viewer = service.getBdvHandle().getViewerPanel();
		final SideViews sideViews = new SideViews(viewer);
		sideViews.animationDurationMillis = Math.max(0, rotationMillis);
		final SlicingViews slicingViews = new SlicingViews(viewer);

		service.addViewAction((ClickBehaviour) (x, y) -> {
			sideViews.resetView(viewer);
			sideViews.animateViewerToFrontView(viewer);
		}, FRONT_VIEW, FRONT_VIEW_KEYS);
		service.addViewAction((ClickBehaviour) (x, y) -> {
			sideViews.resetView(viewer);
			sideViews.animateViewerToSideView(viewer);
		}, SIDE_VIEW, SIDE_VIEW_KEYS);
		service.addViewAction((ClickBehaviour) (x, y) -> {
			//NB: no reset here, we're returning to what was the current view at the last reset
			sideViews.animateViewerToTopView(viewer);
		}, REFERENCE_VIEW, REFERENCE_VIEW_KEYS);

		service.addViewAction((ClickBehaviour) (x, y) -> {
			slicingViews.resetView(viewer);
			viewer.state().setViewerTransform(slicingViews.sameViewShiftedBy(-slicingStep));
		}, SLICE_NEARER, SLICE_NEARER_KEYS);
		service.addViewAction((ClickBehaviour) (x, y) -> {
			slicingViews.resetView(viewer);
			viewer.state().setViewerTransform(slicingViews.sameViewShiftedBy(+slicingStep));
		}, SLICE_FURTHER, SLICE_FURTHER_KEYS);
		return true;
	}

	/** Removes all the navigation actions from the service. */
	public static void removeNavigationActionsFrom(final BdvPromptsService service) {
		for (String action : new String[] { FRONT_VIEW, SIDE_VIEW, REFERENCE_VIEW, SLICE_NEARER, SLICE_FURTHER })
			if (service.getActions().contains(action)) service.removeAction(action);
	}

	// ======================== keymap editor descriptions ========================
	@Plugin(type = CommandDescriptionProvider.class)
	public static class Descriptions extends CommandDescriptionProvider {
		public Descriptions() {
			super(KeyConfigScopes.BIGDATAVIEWER, BdvPromptsService.KEYCONFIG_CONTEXT);
		}

		@Override
		public void getCommandDescriptions(final CommandDescriptions descriptions) {
			descriptions.add(PROMPT, PROMPT_KEYS,
					"To create a box on original data, hold the key, keep holding it and move the mouse, release the key eventually.");
			descriptions.add(PROMPT_CONTRAST, PROMPT_CONTRAST_KEYS,
					  "To create a box on contrast-adjusted data, hold the key, keep holding it and move the mouse, release the key eventually.");
			descriptions.add(REPEAT_PROMPT_NEARER_SLICE, REPEAT_PROMPT_NEARER_SLICE_KEYS,
					  "Repeat the last created box on original data, on a nearer slice.");
			descriptions.add(REPEAT_PROMPT_FURTHER_SLICE, REPEAT_PROMPT_FURTHER_SLICE_KEYS,
					  "Repeat the last created box on original data, on a further slice.");
			descriptions.add(REPEAT_PROMPT_HERE, REPEAT_PROMPT_HERE_KEYS,
					"Repeat the last created box on original data, on the current view.");
			descriptions.add(MULTI_PROMPT, MULTI_PROMPT_KEYS,
					"Drag a box in which seeds are detected and processed iteratively as separate prompts.");
			descriptions.add(TRACKING_PROMPT, TRACKING_PROMPT_KEYS,
					"Drag a box around an object, which is then followed and prompted slice by slice for as long as it is found, on original data.");
			descriptions.add(TRACKING_PROMPT_CONTRAST, TRACKING_PROMPT_CONTRAST_KEYS,
					  "Drag a box around an object, which is then followed and prompted slice by slice for as long as it is found, on contrast-adjusted data.");

			descriptions.add(FRONT_VIEW, FRONT_VIEW_KEYS,
					"Animated 90-degree rotation of the current view around the screen x-axis, through the mouse position.");
			descriptions.add(SIDE_VIEW, SIDE_VIEW_KEYS,
					"Animated 90-degree rotation of the current view around the screen y-axis, through the mouse position.");
			descriptions.add(REFERENCE_VIEW, REFERENCE_VIEW_KEYS,
					"Animated return to the view from before the last front or side view rotation.");
			descriptions.add(SLICE_NEARER, SLICE_NEARER_KEYS,
					"Move the view towards the viewer by a fixed step in global units (zoom-independent).");
			descriptions.add(SLICE_FURTHER, SLICE_FURTHER_KEYS,
					"Move the view away from the viewer by a fixed step in global units (zoom-independent).");
		}
	}
}
