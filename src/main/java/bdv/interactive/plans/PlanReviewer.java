package bdv.interactive.plans;

import bdv.interactive.services.BdvPromptsEvent;
import bdv.interactive.services.BdvPromptsService;
import bdv.viewer.ViewerPanel;
import net.imglib2.realtransform.AffineTransform3D;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A modal dialog to preview a plan (a {@code List<BdvPromptsEvent>}) before it is executed:
 * a slider walks the plan items, and the BDV shows every item's view with its box (see
 * {@link PlanUtils#positionAndShowPromptAccordingToEvent(BdvPromptsService, BdvPromptsEvent)}).
 * The dialog ends with "Proceed" (also Enter) or "Cancel" (also Escape, or closing the window).
 * <p>
 * The dialog only reviews, it doesn't execute anything; the client checks the returned value
 * and possibly executes the plan itself, e.g.:
 * <pre>
 *   if (PlanReviewer.review(plan, promptsService)) runMyPlan(plan);
 * </pre>
 * On "Cancel", the BDV is returned to the view (and timepoint) it showed before the dialog
 * opened; on "Proceed", the BDV is left at the last reviewed item. In both cases, the box
 * is hidden when the dialog closes.
 * <p>
 * As the dialog is modal to the BDV's window, the user can't change the BDV's view meanwhile.
 */
public final class PlanReviewer {
	private PlanReviewer() {}

	/** Like {@link #review(List, BdvPromptsService, String)} with a generic dialog title. */
	public static boolean review(final List<BdvPromptsEvent> plan, final BdvPromptsService service) {
		return review(plan, service, "Review the planned prompts");
	}

	/**
	 * Opens the dialog and blocks until it is closed. Can be called from any thread; when called
	 * from the AWT Event Dispatch Thread (e.g. from within a {@code BdvPromptsListener}), the BDV
	 * keeps repainting while the dialog is open, as usual with modal dialogs.
	 *
	 * @return true if the user chose "Proceed"; false on "Cancel", or if the plan is empty
	 */
	public static boolean review(final List<BdvPromptsEvent> plan, final BdvPromptsService service, final String dialogWindowTitle) {
		if (plan == null || plan.isEmpty()) return false;
		if (SwingUtilities.isEventDispatchThread()) return showDialog(plan, service, dialogWindowTitle);

		final AtomicBoolean result = new AtomicBoolean(false);
		try {
			SwingUtilities.invokeAndWait(() -> result.set(showDialog(plan, service, dialogWindowTitle)));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (InvocationTargetException e) {
			throw new RuntimeException("Plan review failed", e.getCause());
		}
		return result.get();
	}

	private static boolean showDialog(final List<BdvPromptsEvent> plan, final BdvPromptsService service, final String title) {
		final ViewerPanel viewer = service.getBdvHandle().getViewerPanel();
		final AffineTransform3D originalView = viewer.state().getViewerTransform();
		final int originalTimepoint = viewer.state().getCurrentTimepoint();

		final Window owner = SwingUtilities.getWindowAncestor(viewer);
		final JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.DOCUMENT_MODAL);
		final AtomicBoolean proceed = new AtomicBoolean(false);

		// --- the slider over the plan items, and a line about the current item
		final int lastIdx = plan.size() - 1;
		final JSlider slider = new JSlider(0, lastIdx, 0);
		slider.setMajorTickSpacing(Math.max(1, lastIdx / 10));
		slider.setPaintTicks(lastIdx > 0);
		slider.setSnapToTicks(false);
		slider.setEnabled(lastIdx > 0);

		final JLabel info = new JLabel();
		final Runnable showCurrentItem = () -> {
			final int idx = slider.getValue();
			final BdvPromptsEvent item = plan.get(idx);
			PlanUtils.positionAndShowPromptAccordingToEvent(service, item);
			info.setText("Item " + (idx + 1) + " of " + plan.size() + ": box ["
					+ item.getMinX() + "," + item.getMinY() + " -> " + item.getMaxX() + "," + item.getMaxY()
					+ "] (" + item.getWidth() + "x" + item.getHeight() + " px)");
		};
		slider.addChangeListener(e -> showCurrentItem.run());

		// --- the buttons
		final JButton proceedButton = new JButton("Proceed");
		proceedButton.addActionListener(e -> {
			proceed.set(true);
			dialog.dispose();
		});
		final JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> dialog.dispose());

		final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(cancelButton);
		buttons.add(proceedButton);

		// --- the layout
		final JPanel content = new JPanel(new BorderLayout(0, 8));
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
		content.add(new JLabel("Move the slider (or use the arrow keys) to see the planned prompts in the BigDataViewer."),
				BorderLayout.NORTH);
		content.add(slider, BorderLayout.CENTER);
		final JPanel bottom = new JPanel(new BorderLayout());
		bottom.add(info, BorderLayout.WEST);
		bottom.add(buttons, BorderLayout.SOUTH);
		content.add(bottom, BorderLayout.SOUTH);
		dialog.setContentPane(content);

		// --- Enter proceeds, Escape (or closing the window) cancels
		dialog.getRootPane().setDefaultButton(proceedButton);
		dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke("ESCAPE"), "cancelPlanReview");
		dialog.getRootPane().getActionMap().put("cancelPlanReview", new AbstractAction() {
			@Override
			public void actionPerformed(final ActionEvent e) { dialog.dispose(); }
		});
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(final WindowEvent e) { slider.requestFocusInWindow(); }
		});

		showCurrentItem.run();
		dialog.pack();
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true); //blocks until disposed

		service.hideBox();
		if (!proceed.get()) {
			if (viewer.state().getCurrentTimepoint() != originalTimepoint)
				viewer.state().setCurrentTimepoint(originalTimepoint);
			viewer.state().setViewerTransform(originalView);
		}
		return proceed.get();
	}
}
