package bdv.interactive.services;

import bdv.KeyConfigContexts;
import bdv.ui.keymap.Keymap;
import bdv.ui.keymap.KeymapManager;
import bdv.util.BdvHandle;
import bdv.viewer.OverlayRenderer;
import bdv.viewer.ViewerPanel;
import org.scijava.ui.behaviour.Behaviour;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.InputTrigger;
import org.scijava.ui.behaviour.InputTriggerMap;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Lets the user drag a rectangular "rubber-band" box over a BigDataViewer
 * window, displays it while dragging, and notifies registered listeners
 * once the dragging is over.
 * <p>
 * The drags are regular, <b>named</b> ui-behaviour {@link DragBehaviour}s, one per
 * <i>action</i> created with {@link #addInsertPromptAction(String, String...)}. Their triggers
 * come from the BDV's keymap ({@link BdvHandle#getKeymapManager()}, context
 * {@value KeyConfigContexts#BIGDATAVIEWER}): the provided default triggers are used
 * only if the keymap doesn't know the action yet, and a user's re-mapping done in the
 * keymap editor takes effect immediately. To have the actions listed (with
 * descriptions) in the BDV's keymap editor, publish them additionally with a
 * {@code CommandDescriptionProvider}, see {@code ai.nets.samj.bdv.SamjBdvActions}.
 * <p>
 * Listeners are registered per action. Each listener is coupled with a <i>guard</i>,
 * a {@link BooleanSupplier}: when a drag of an action finishes, only the listeners
 * of that action whose guard currently returns true are notified, e.g.
 * <pre>
 *   bdvPromptsService.addInsertPromptAction("samj prompt", "L");
 *   bdvPromptsService.addListener("samj prompt", myButton::isEnabled, e -&gt; doSomething(e));
 * </pre>
 * A drag is not even started (no box is displayed) unless at least one guard
 * of the action evaluates to true at the moment the dragging begins.
 * Guards are evaluated again when the drag ends. Only one drag can be
 * in progress at a time.
 * <p>
 * Besides the user-driven box, a box can be displayed programmatically via
 * {@link #showBox(int, int, int, int)} and {@link #hideBox()}, which is handy
 * for "replaying" prompts (e.g. while walking through slices).
 * <p>
 * Listeners are called on the AWT Event Dispatch Thread.
 */
public class BdvPromptsService {

	public enum LineStyle { SOLID, DASHED, DOTTED }

	/** The keymap context in which the actions are registered. */
	public static final String KEYCONFIG_CONTEXT = KeyConfigContexts.BIGDATAVIEWER;

	// ======================== construction & disposal ========================
	public BdvPromptsService(final BdvHandle bdv) {
		this(bdv, "bdvPromptsService_" + INSTANCE_COUNTER.incrementAndGet());
	}

	// ensures no two BdvPromptsServices (unless caller messes it up himself with the c'tor above)
	// clash their names, their "binding spaces"
	private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

	/**
	 * @param bindingsName Name under which this service's trigger and behaviour
	 *                     maps are installed into the BDV's trigger bindings.
	 */
	public BdvPromptsService(final BdvHandle bdv, final String bindingsName) {
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

		canvasWidth = viewer.getDisplay().getWidth();
		canvasHeight = viewer.getDisplay().getHeight();
		viewer.getDisplay().overlays().add(overlay);
		viewer.getDisplayComponent().addKeyListener(pressedKeysMonitor);
	}

	private final BdvHandle bdv;
	private final ViewerPanel viewer;
	private final String bindingsName;
	private final Keymap keymap;
	private final Behaviours behaviours;
	private final TriggerBehaviourBindings triggerBindings;

	/** Follow the user's changes of the keymap (edits in the keymap editor, or switching to another keymap). */
	private final Keymap.UpdateListener keymapListener = this::keymapUpdated;

	private synchronized void keymapUpdated() {
		//NB: 'false' keeps the current triggers of actions that the new keymap doesn't know
		behaviours.updateKeyConfig(keymap.getConfig(), false);
	}

	public BdvHandle getBdvHandle() { return bdv; }

	/** Removes all actions, listeners and the overlay from the BDV. The object is not usable afterwards. */
	public synchronized void dispose() {
		cancelDrag();
		if (keymap != null) keymap.updateListeners().remove(keymapListener);
		triggerBindings.removeInputTriggerMap(bindingsName);
		triggerBindings.removeBehaviourMap(bindingsName);
		behaviours.getInputTriggerMap().clear();
		behaviours.getBehaviourMap().clear();
		listeners.clear();
		viewer.getDisplay().overlays().remove(overlay);
		viewer.getDisplayComponent().removeKeyListener(pressedKeysMonitor);
		requestRepaint();
	}

	// ======================== actions ========================
	/**
	 * Creates a new box prompting action, as a named drag behaviour.
	 * Triggers follow the ui-behaviour syntax, e.g. "L", "shift L", "ctrl button1".
	 * Adding an existing action does nothing.
	 *
	 * @param actionName unique name of the action, as it appears in the keymap editor
	 */
	public synchronized void addInsertPromptAction(final String actionName, final String... defaultTriggers) {
		addAction(new BoxDrag(actionName), actionName, defaultTriggers);
	}

	/**
	 * Creates a new "repeat the last box" action, as a named click behaviour (keymap-driven,
	 * just like {@link #addInsertPromptAction(String, String...)}). When triggered, its listeners receive
	 * a {@link BdvPromptsEvent} with the <i>last box</i> (in screen coordinates) and with
	 * the <i>current</i> view geometry (viewer transform, timepoint, canvas size).
	 * The last box is the last one finished by the user (with any action), or the last one
	 * displayed with {@link #showBox(int, int, int, int)}, whichever came later.
	 * Nothing happens if there's been no box yet, or no guard of this action is open.
	 *
	 * @param actionName unique name of the action, as it appears in the keymap editor
	 */
	public synchronized void addRepeatAction(final String actionName, final String... defaultTriggers) {
		addAction((ClickBehaviour) (x, y) -> repeatLastBox(actionName), actionName, defaultTriggers);
	}

	private void repeatLastBox(final String actionName) {
		if (!enabled || isDragging()) return;
		if (!isAnyGuardOpen(actionName)) return;

		final BdvPromptsEvent event = new BdvPromptsEvent(actionName,
				lastBox[0], lastBox[1], lastBox[2], lastBox[3],
				canvasWidth, canvasHeight,
				viewer.state().getViewerTransform(), viewer.state().getCurrentTimepoint());
		notifyListeners(actionName, event);
	}

	/**
	 * Installs the action. The actual trigger(s) are taken from the keymap if it knows this
	 * action; otherwise the provided default triggers are used (and recorded into the keymap).
	 *
	 * This is a common code and is operated from public add__Action() methods from this class.
	 * The callers shall be synchronized methods already.
	 */
	private void addAction(final Behaviour daAction, final String actionName, final String... defaultTriggers) {
		if (listeners.containsKey(actionName)) {
			System.out.println("Silently skipping registration of an action " + actionName
					  + " with triggers: "+ Arrays.toString(defaultTriggers));
			return;
		}
		listeners.put(actionName, new CopyOnWriteArrayList<>());
		behaviours.behaviour(daAction, actionName, defaultTriggers);
	}

	/** Uninstalls the action from BDV, together with all its listeners. */
	public synchronized void removeAction(final String actionName) {
		if (listeners.remove(actionName) == null) {
			System.out.println("Silently skipping de-registration of a not-previously-registered action " + actionName);
			return;
		}
		if (actionName.equals(userDrawingAction)) cancelDrag();

		final InputTriggerMap triggers = behaviours.getInputTriggerMap();
		for (Map.Entry<InputTrigger, Set<String>> binding : triggers.getBindings().entrySet())
			if (binding.getValue().contains(actionName)) triggers.remove(binding.getKey(), actionName);
		behaviours.getBehaviourMap().remove(actionName);
	}

	/** @return names of the currently installed actions */
	public Set<String> getActions() {
		return Collections.unmodifiableSet(new HashSet<>(listeners.keySet()));
	}

	/** @return the triggers currently bound to the action, e.g. for showing them in a GUI tooltip */
	public Set<InputTrigger> getTriggers(final String actionName) {
		final Set<InputTrigger> forReportTriggers = new HashSet<>();
		for (Map.Entry<InputTrigger, Set<String>> binding : behaviours.getInputTriggerMap().getBindings().entrySet())
			if (binding.getValue().contains(actionName)) forReportTriggers.add(binding.getKey());
		return forReportTriggers;
	}

	// ======================== listeners per action ========================
	private static final class GuardedListener {
		final BooleanSupplier guard;
		final BdvPromptsListener listener;
		GuardedListener(final BooleanSupplier guard, final BdvPromptsListener listener) {
			this.guard = guard;
			this.listener = listener;
		}
	}

	private final Map<String, List<GuardedListener>> listeners = new ConcurrentHashMap<>();

	/** Registers the listener for the action such that it is always notified. */
	public void addListener(final String actionName, final BdvPromptsListener listener) {
		addListener(actionName, () -> true, listener);
	}

	/**
	 * Registers the listener for the action; the listener is notified only if the
	 * guard returns true at the moment the drag finishes. The same listener can be
	 * registered for several actions.
	 *
	 * @throws IllegalArgumentException if the action has not been {@link #addInsertPromptAction(String, String...) added}
	 */
	public void addListener(final String actionName,
	                        final BooleanSupplier guard,
	                        final BdvPromptsListener listener) {
		final List<GuardedListener> list = listeners.get(actionName);
		if (list == null)
			throw new IllegalArgumentException("Unknown action '" + actionName + "', add*Action() it first.");
		list.add(new GuardedListener(guard, listener));
	}

	/**
	 * Unregisters all occurrences of this listener from this action.
	 * The action itself remains installed.
	 *
	 * @return true if anything was removed
	 */
	public boolean removeListener(final String actionName, final BdvPromptsListener listener) {
		final List<GuardedListener> list = listeners.get(actionName);
		return list != null && list.removeIf(gl -> gl.listener == listener);
	}

	/** Unregisters this listener from all actions. */
	public void removeListener(final BdvPromptsListener listener) {
		for (List<GuardedListener> list : listeners.values()) list.removeIf(gl -> gl.listener == listener);
	}

	private boolean isAnyGuardOpen(final String actionName) {
		final List<GuardedListener> list = listeners.get(actionName);
		if (list == null) return false;
		for (GuardedListener gl : list) if (gl.guard.getAsBoolean()) return true;
		return false;
	}

	private void notifyListeners(final String actionName, final BdvPromptsEvent event) {
		final List<GuardedListener> list = listeners.get(actionName);
		if (list == null) return;
		for (GuardedListener gl : list) {
			if (!gl.guard.getAsBoolean()) continue;
			try {
				gl.listener.onPromptEntered(event);
			} catch (RuntimeException e) {
				//one faulty listener should not prevent the others from being notified
				e.printStackTrace();
			}
		}
	}

	// ======================== enabling & settings ========================
	/** An indicator whether this whole service is enabled to operate. */
	private volatile boolean enabled = true;

	/** When disabled, the actions remain installed, but they are not allowed to start,
	 * and are not started. A drag in progress is canceled. */
	public void setEnabled(final boolean enabled) {
		this.enabled = enabled;
		if (!enabled) cancelDrag();
	}
	public boolean isEnabled() { return enabled; }

	private volatile int minimalBoxSize = 2;

	/** Boxes narrower or lower than this (in screen pixels) are silently dropped, no listener is notified. Default is 2. */
	public void setMinimalBoxSize(final int pixels) { this.minimalBoxSize = Math.max(1, pixels); }
	public int getMinimalBoxSize() { return minimalBoxSize; }

	// ======================== appearance ========================
	private volatile Color defaultColor = Color.GREEN;
	private volatile Stroke defaultStroke = createStroke(2.0f, LineStyle.SOLID);
	private final Map<String, Color> colorPerAction = new ConcurrentHashMap<>();
	private final Map<String, Stroke> strokePerAction = new ConcurrentHashMap<>();

	/** Use a specific color for boxes created with this action */
	public void setBoxColor(final String actionName, final Color color) {
		colorPerAction.put(actionName, color);
		requestRepaint();
	}

	/**
	 * Use a specific line style and width for boxes created with this action.
	 * The line styles are {@link LineStyle#SOLID}, {@link LineStyle#DASHED} or {@link LineStyle#DOTTED}.
	 */
	public void setBoxStyle(final String actionName, final LineStyle lineStyle, final float thickness) {
		strokePerAction.put(actionName, createStroke(thickness, lineStyle));
		requestRepaint();
	}

	protected static Stroke createStroke(final float thickness, final LineStyle style) {
		switch (style) {
			case DASHED:
				return new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f,
						new float[] {4 * thickness + 4, 2 * thickness + 2}, 0.0f);
			case DOTTED:
				return new BasicStroke(thickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f,
						new float[] {0.0f, 2 * thickness + 2}, 0.0f);
			default:
				return new BasicStroke(thickness);
		}
	}

	// ======================== the box state & drawing ========================
	//NB: the box state is touched from the EDT (input events and painting),
	//    and possibly from client threads via showBox()/hideBox()
	//NB: both _DrawingAction refer to name of an action because of which the box
	//    shall be displayed/painted; both also work as flags to draw the box or not
	private static final String NO_ACTIVE_ACTION = "IndicatorOfNoAction";
	private volatile boolean isBoxShownProgrammatically = false;
	private volatile String userDrawingAction = NO_ACTIVE_ACTION; //gets a different value while user drags
	private volatile String programmaticDrawingAction = NO_ACTIVE_ACTION; //whose appearance the programmatic box takes
	private volatile int sx, sy, ex, ey; //box corners as the user dragged them (not normalized)
	private volatile int canvasWidth, canvasHeight;
	private final int[] lastBox = new int[4]; //see addRepeatAction() and BoxDraw.end()

	/** @return true while the user is dragging a box */
	public boolean isDragging() { return userDrawingAction != NO_ACTIVE_ACTION; }

	/** Aborts the drag in progress (if any) without notifying anybody. */
	public void cancelDrag() {
		if (userDrawingAction == NO_ACTIVE_ACTION) return;
		userDrawingAction = NO_ACTIVE_ACTION;
		requestRepaint();
	}

	/**
	 * Note that a memory to remember the last box is allocated at construction of this object,
	 * so this method always returns something even when no drag (box) has occured so far.
	 *
	 * @return the last box as {x_min, y_min, x_max, y_max} (see {@link #addRepeatAction(String, String...)}).
	 */
	public int[] getLastBox() {
		return lastBox.clone();
	}

	/**
	 * Displays a box (in screen pixel coordinates) on behalf of the client; this
	 * has no relation to listeners. The box remains visible until {@link #hideBox()}
	 * or until the user starts a new drag.
	 */
	public void showBox(final int x0, final int y0, final int x1, final int y1) {
		showBox(NO_ACTIVE_ACTION, x0, y0, x1, y1);
	}

	/**
	 * Like {@link #showBox(int, int, int, int)}, but the box is drawn with the color and style
	 * of the given action (see {@link #setBoxColor(String, Color)}, {@link #setBoxStyle(String, LineStyle, float)}).
	 */
	public void showBox(final String useStyleOfThisAction, final int x0, final int y0, final int x1, final int y1) {
		if (isDragging()) return;
		lastBox[0] = sx = Math.min(x0, x1);
		lastBox[1] = sy = Math.min(y0, y1);
		lastBox[2] = ex = Math.max(x0, x1);
		lastBox[3] = ey = Math.max(y0, y1);
		programmaticDrawingAction = useStyleOfThisAction;
		isBoxShownProgrammatically = true;
		requestRepaint();
	}

	public void hideBox() {
		isBoxShownProgrammatically = false;
		requestRepaint();
	}

	protected void requestRepaint() {
		viewer.getDisplay().repaint();
	}

	private final OverlayRenderer overlay = new OverlayRenderer() {
		@Override
		public void drawOverlays(final Graphics g) {
			final String dragAction = userDrawingAction; // a local (unmodifiable) copy to render consistently
			if (dragAction == NO_ACTIVE_ACTION && !isBoxShownProgrammatically) return;
			final String action = dragAction != NO_ACTIVE_ACTION ? dragAction : programmaticDrawingAction;

			final Graphics2D g2 = (Graphics2D) g;
			final Stroke origStroke = g2.getStroke();
			final Color origColor = g2.getColor();
			g2.setColor(colorPerAction.getOrDefault(action, defaultColor));
			g2.setStroke(strokePerAction.getOrDefault(action, defaultStroke));
			g2.drawRect(Math.min(sx, ex), Math.min(sy, ey), Math.abs(ex - sx), Math.abs(ey - sy));
			g2.setStroke(origStroke);
			g2.setColor(origColor);
		}

		@Override
		public void setCanvasSize(final int width, final int height) {
			canvasWidth = width;
			canvasHeight = height;
		}
	};

	// ======================== the dragging itself ========================
	/**
	 * For key-triggered drags with modifiers (e.g. "shift L"), the end of the drag
	 * may not be reported reliably (e.g. when the keys are released in a "wrong"
	 * order). Therefore, if a drag started while keys were held, the drag is
	 * closed as soon as no key is pressed anymore. Mouse-button-only drags
	 * are not affected.
	 */
	private final PressedKeysMonitor pressedKeysMonitor = new PressedKeysMonitor();

	private static final class PressedKeysMonitor extends KeyAdapter {
		private final Set<Integer> pressed = Collections.synchronizedSet(new HashSet<>());
		@Override
		public void keyPressed(final KeyEvent e) { pressed.add(e.getKeyCode()); }
		@Override
		public void keyReleased(final KeyEvent e) { pressed.remove(e.getKeyCode()); }
		boolean isAnyKeyPressed() { return !pressed.isEmpty(); }
	}

	private class BoxDrag implements DragBehaviour {
		BoxDrag(final String actionName) {
			this.action = actionName;
		}
		private final String action;
		private boolean startedWithKeysHeld = false;

		@Override
		public void init(final int x, final int y) {
			if (!enabled || userDrawingAction != NO_ACTIVE_ACTION) return;
			if (!isAnyGuardOpen(action)) return;

			startedWithKeysHeld = pressedKeysMonitor.isAnyKeyPressed();
			isBoxShownProgrammatically = false;
			sx = x; sy = y; ex = x; ey = y;
			userDrawingAction = action;
			requestRepaint();
		}

		@Override
		public void drag(final int x, final int y) {
			if (!action.equals(userDrawingAction)) return;
			ex = x; ey = y;
			requestRepaint();
			if (startedWithKeysHeld && !pressedKeysMonitor.isAnyKeyPressed()) end(x, y);
		}

		@Override
		public void end(final int x, final int y) {
			if (!action.equals(userDrawingAction)) return; //TODO these are super expensive operations!!!
			ex = x; ey = y;
			userDrawingAction = NO_ACTIVE_ACTION;
			requestRepaint();

			//normalize and clamp into the canvas
			final int maxX = Math.max(canvasWidth - 1, 0);
			final int maxY = Math.max(canvasHeight - 1, 0);
			final int x0 = clamp(Math.min(sx, ex), maxX), x1 = clamp(Math.max(sx, ex), maxX);
			final int y0 = clamp(Math.min(sy, ey), maxY), y1 = clamp(Math.max(sy, ey), maxY);
			if (x1 - x0 + 1 < minimalBoxSize || y1 - y0 + 1 < minimalBoxSize) return;

			lastBox[0] = x0;
			lastBox[1] = y0;
			lastBox[2] = x1;
			lastBox[3] = y1;
			final BdvPromptsEvent event = new BdvPromptsEvent(action, x0, y0, x1, y1,
					canvasWidth, canvasHeight,
					viewer.state().getViewerTransform(), viewer.state().getCurrentTimepoint());
			notifyListeners(action, event);
		}
	}

	private static int clamp(final int v, final int max) {
		return Math.min(Math.max(v, 0), max);
	}
}
