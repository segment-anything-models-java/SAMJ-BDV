package bdv.interactive.services;

import bdv.KeyConfigContexts;
import bdv.KeyConfigScopes;
import bdv.interactive.views.SideViews;
import bdv.interactive.views.SlicingViews;
import bdv.ui.keymap.Keymap;
import bdv.ui.keymap.KeymapManager;
import bdv.util.BdvHandle;
import bdv.viewer.ViewerPanel;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keyboard navigation that complements BDV's own, bound to named actions
 * that take their triggers from the BDV's keymap (context
 * {@value KeyConfigContexts#BIGDATAVIEWER}), just like {@link BdvPromptsService} does:
 * <ul>
 *   <li><b>Side views</b> ({@link SideViews}): animated 90-degree rotations around the
 *       current <i>screen</i> x- or y-axis (through the mouse position), and back to the
 *       view from before the rotation. Unlike BDV's "align XY/ZY/XZ plane", which snap
 *       onto the <i>data</i> axes, these give views perpendicular to any oblique cut.</li>
 *   <li><b>Slicing</b> ({@link SlicingViews}): moves the view along its viewing direction
 *       by a fixed step in <i>global</i> units, regardless of zoom. Unlike BDV's
 *       "forward z"/"backward z", whose step is one <i>screen</i> unit and thus
 *       depends on the zoom.</li>
 * </ul>
 * Both groups make sense only in 3D, and are thus not installed in a 2D BDV.
 * The {@link Descriptions} provider makes the actions listed in the BDV keymap editor.
 */
public class ViewNavigationBehaviours {

	// ======================== action names & default triggers ========================
	public static final String FRONT_VIEW = "side views: rotate to front view";
	public static final String[] FRONT_VIEW_KEYS = new String[] { "ctrl I" };

	public static final String SIDE_VIEW = "side views: rotate to side view";
	public static final String[] SIDE_VIEW_KEYS = new String[] { "ctrl J" };

	public static final String REFERENCE_VIEW = "side views: rotate back to reference view";
	public static final String[] REFERENCE_VIEW_KEYS = new String[] { "ctrl K" };

	public static final String SLICE_CLOSER = "slicing views: move one step closer";
	public static final String[] SLICE_CLOSER_KEYS = new String[] { "ctrl N" };

	public static final String SLICE_FURTHER = "slicing views: move one step further";
	public static final String[] SLICE_FURTHER_KEYS = new String[] { "ctrl M" };

	public static final String KEYCONFIG_CONTEXT = KeyConfigContexts.BIGDATAVIEWER;

	// ======================== construction & disposal ========================
	/** Creates the object, but installs no action yet; see {@link #addAllActions()}. */
	public ViewNavigationBehaviours(final BdvHandle bdv) {
		this(bdv, "viewNavigationBehaviours_" + INSTANCE_COUNTER.incrementAndGet());
	}

	private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

	/**
	 * @param bindingsName Name under which the trigger and behaviour maps
	 *                     are installed into the BDV's trigger bindings.
	 */
	public ViewNavigationBehaviours(final BdvHandle bdv, final String bindingsName) {
		this.bdv = bdv;
		this.viewer = bdv.getViewerPanel();
		this.bindingsName = bindingsName;

		final KeymapManager keymapManager = bdv.getKeymapManager();
		this.keymap = keymapManager != null ? keymapManager.getForwardSelectedKeymap() : null;
		final InputTriggerConfig config = keymap != null ? keymap.getConfig() : new InputTriggerConfig();

		this.behaviours = new Behaviours(config, KEYCONFIG_CONTEXT);
		this.triggerBindings = bdv.getTriggerbindings();
		behaviours.install(triggerBindings, bindingsName);
		if (keymap != null) keymap.updateListeners().add(keymapListener);

		this.sideViews = new SideViews(viewer);
		this.slicingViews = new SlicingViews(viewer);
	}

	private final BdvHandle bdv;
	private final ViewerPanel viewer;
	private final String bindingsName;
	private final Keymap keymap;
	private final Behaviours behaviours;
	private final TriggerBehaviourBindings triggerBindings;

	private final SideViews sideViews;
	private final SlicingViews slicingViews;

	private final Keymap.UpdateListener keymapListener = this::keymapUpdated;

	private synchronized void keymapUpdated() {
		//NB: 'false' keeps the current triggers of actions that the new keymap doesn't know
		behaviours.updateKeyConfig(keymap.getConfig(), false);
	}

	public BdvHandle getBdvHandle() { return bdv; }

	/** Removes all actions from the BDV. The object is not usable afterwards. */
	public synchronized void dispose() {
		if (keymap != null) keymap.updateListeners().remove(keymapListener);
		triggerBindings.removeInputTriggerMap(bindingsName);
		triggerBindings.removeBehaviourMap(bindingsName);
		behaviours.getInputTriggerMap().clear();
		behaviours.getBehaviourMap().clear();
	}

	// ======================== installing the actions ========================
	/** @return true if this is a 2D BDV, in which case no navigation is installed. */
	public boolean is2D() {
		return viewer.getOptionValues().is2D();
	}

	/** Installs both groups, i.e., {@link #addSideViewsActions()} and {@link #addSlicingActions()}. */
	public void addAllActions() {
		addSideViewsActions();
		addSlicingActions();
	}

	/**
	 * Installs the {@link #FRONT_VIEW}, {@link #SIDE_VIEW} and {@link #REFERENCE_VIEW} actions.
	 * The front and side views rotate away from the <i>current</i> view, which becomes the
	 * reference view to which {@link #REFERENCE_VIEW} returns.
	 *
	 * @return false (and nothing installed) if this is a 2D BDV
	 */
	public synchronized boolean addSideViewsActions() {
		if (is2D()) return false;
		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			if (!enabled) return;
			sideViews.resetView(viewer);
			sideViews.animateViewerToFrontView(viewer);
		}, FRONT_VIEW, FRONT_VIEW_KEYS);
		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			if (!enabled) return;
			sideViews.resetView(viewer);
			sideViews.animateViewerToSideView(viewer);
		}, SIDE_VIEW, SIDE_VIEW_KEYS);
		behaviours.behaviour((ClickBehaviour) (x, y) -> {
			if (!enabled) return;
			//NB: no reset here, we're returning to what was the current view at the last reset
			sideViews.animateViewerToTopView(viewer);
		}, REFERENCE_VIEW, REFERENCE_VIEW_KEYS);
		return true;
	}

	/**
	 * Installs the {@link #SLICE_CLOSER} and {@link #SLICE_FURTHER} actions,
	 * which move by {@link #getSlicingStep()} global units.
	 *
	 * @return false (and nothing installed) if this is a 2D BDV
	 */
	public synchronized boolean addSlicingActions() {
		if (is2D()) return false;
		behaviours.behaviour((ClickBehaviour) (x, y) -> moveAlongViewAxis(-slicingStep),
				SLICE_CLOSER, SLICE_CLOSER_KEYS);
		behaviours.behaviour((ClickBehaviour) (x, y) -> moveAlongViewAxis(+slicingStep),
				SLICE_FURTHER, SLICE_FURTHER_KEYS);
		return true;
	}

	private void moveAlongViewAxis(final double delta) {
		if (!enabled) return;
		slicingViews.resetView(viewer);
		viewer.state().setViewerTransform(slicingViews.sameViewShiftedBy(delta));
	}

	// ======================== settings ========================
	private volatile boolean enabled = true;

	/** When disabled, the actions remain installed but do nothing. */
	public void setEnabled(final boolean enabled) { this.enabled = enabled; }
	public boolean isEnabled() { return enabled; }

	private volatile double slicingStep = 1.0;

	/**
	 * The step of the slicing actions, in global units (the units shown in BDV's top-right
	 * corner). Default is 1.0, which is one pixel for data with unit pixel size; for calibrated
	 * data, set it to, e.g., the voxel size along the typical viewing direction.
	 */
	public void setSlicingStep(final double globalUnits) {
		if (globalUnits <= 0)
			throw new IllegalArgumentException("Slicing step must be positive, got " + globalUnits);
		this.slicingStep = globalUnits;
	}
	public double getSlicingStep() { return slicingStep; }

	/** Duration of the side views rotations, in milliseconds. Default is 1000. */
	public void setAnimationDuration(final int millis) {
		sideViews.animationDurationMillis = Math.max(0, millis);
	}
	public int getAnimationDuration() { return sideViews.animationDurationMillis; }

	// ======================== keymap editor descriptions ========================
	@Plugin(type = CommandDescriptionProvider.class)
	public static class Descriptions extends CommandDescriptionProvider {
		public Descriptions() {
			super(KeyConfigScopes.BIGDATAVIEWER, KEYCONFIG_CONTEXT);
		}

		@Override
		public void getCommandDescriptions(final CommandDescriptions descriptions) {
			descriptions.add(FRONT_VIEW, FRONT_VIEW_KEYS,
					"Animated 90-degree rotation of the current view around the screen x-axis, through the mouse position.");
			descriptions.add(SIDE_VIEW, SIDE_VIEW_KEYS,
					"Animated 90-degree rotation of the current view around the screen y-axis, through the mouse position.");
			descriptions.add(REFERENCE_VIEW, REFERENCE_VIEW_KEYS,
					"Animated return to the view from before the last front or side view rotation.");
			descriptions.add(SLICE_CLOSER, SLICE_CLOSER_KEYS,
					"Move the view towards the viewer by a fixed step in global units (zoom-independent).");
			descriptions.add(SLICE_FURTHER, SLICE_FURTHER_KEYS,
					"Move the view away from the viewer by a fixed step in global units (zoom-independent).");
		}
	}
}
