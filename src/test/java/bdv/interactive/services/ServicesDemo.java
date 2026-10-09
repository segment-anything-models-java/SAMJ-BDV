package bdv.interactive.services;

import bdv.interactive.behaviours.BdvPromptsBehaviours;
import bdv.interactive.plans.SeedsUtils;
import bdv.interactive.plans.PlanReviewer;
import bdv.interactive.plans.PlanUtils;
import bdv.interactive.plans.SeedsPlanner;
import bdv.interactive.plans.TrackingPlanner;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvStackSource;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.interactive.services.OriginalViewsService.CapturedView;
import ij.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RealLocalizable;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shows how the services are meant to be wired together; mimics the old
 * "L" (original pixels) and "shift L" (contrast-adjusted pixels) prompts.
 * <ul>
 *   <li>hold L and move the mouse to drag a box, release L to finish it</li>
 *   <li>hold shift+L for the same on contrast-adjusted pixels</li>
 *   <li>ctrl+COMMA (BDV preferences, "Keymap" page) lists the actions in the keymap editor, where they can be re-mapped</li>
 *   <li>press D to toggle the guard of the "L" listener (simulating a GUI checkbox)</li>
 *   <li>ctrl I / ctrl J rotate to front / side view, ctrl K rotates back; ctrl N / ctrl M move one slice closer / further</li>
 *   <li>hold J to drag a box in which seeds are found and prompted one by one; the seeds are pixels
 *       above the lower end of the display range, so open the brightness dialog (S) and raise its
 *       minimum to, e.g., 500 first (otherwise everything is foreground, touching the box border)</li>
 *   <li>hold K to drag a box over a bright cube, which is then followed and prompted slice by slice</li>
 *   <li>both J and K first open a dialog to review the plan; "Proceed" executes it, "Cancel" drops it</li>
 * </ul>
 */
public class ServicesDemo {
	public static void main(String[] args) {
		ImageJ ij = new ImageJ();
		ij.show();

		final Img<UnsignedShortType> img = ArrayImgs.unsignedShorts(256, 256, 64);
		final Cursor<UnsignedShortType> c = img.localizingCursor();
		while (c.hasNext()) {
			c.fwd();
			c.get().set((c.getIntPosition(0) / 32 + c.getIntPosition(1) / 32 + c.getIntPosition(2) / 8) % 2 * 1000 + 100);
		}

		final BdvStackSource<UnsignedShortType> bdvStackSource = BdvFunctions.show(img, "demo");
		final BdvHandle bdv = bdvStackSource.getBdvHandle();

		// --- the three services, created once, handed to whoever needs them
		final BdvPromptsService bdvPromptsService = new BdvPromptsService(bdv);
		final OriginalViewsService originalViewsService = new OriginalViewsService(bdv);
		final ConvertedViewsService convertedViewsService = new ConvertedViewsService(bdv, bdvStackSource.getSources().get(0));

		// --- named actions; triggers come from BDV's keymap (defaults if unknown there)
		BdvPromptsBehaviours.addPromptActionsTo(bdvPromptsService);
		// --- extra 3D navigation: ctrl I/J/K side views, ctrl N/M slicing
		BdvPromptsBehaviours.addNavigationActionsTo(bdvPromptsService);

		// --- a "module" that works on original pixels, and caches its view image
		final AtomicBoolean moduleEnabled = new AtomicBoolean(true);
		//NB: -1 is never a valid counter value, so the very first prompt always captures a view
		final long[] myChangeCounters = new long[] {-1, -1};
		bdvPromptsService.addListener(BdvPromptsBehaviours.PROMPT, moduleEnabled::get, e -> {
			final boolean isNewView = originalViewsService.hasChangedSince(myChangeCounters[0]);
			if (isNewView) {
				myChangeCounters[0] = originalViewsService.getChangeCounter();
				CapturedView<FloatType> view = originalViewsService.getCurrentView(
						bdvStackSource.getSources().get(0).getSpimSource(), new FloatType());
				report("original ", e, view, isNewView);
				ImageJFunctions.show(view.getImage(), "original");
			} else {
				System.out.println("No new original view");
			}
		});

		final BdvPromptsListener repeatEventHandler = e -> {
			final boolean isNewView = originalViewsService.hasChangedSince(myChangeCounters[0]);
			if (isNewView) {
				myChangeCounters[0] = originalViewsService.getChangeCounter();
				System.out.println("REPEAT: OOOriginal view");
			} else {
				System.out.println("REPEAT: No new original view");
			}
		};
		bdvPromptsService.addListener(BdvPromptsBehaviours.REPEAT_PROMPT_HERE, moduleEnabled::get, repeatEventHandler);
		bdvPromptsService.addListener(BdvPromptsBehaviours.REPEAT_PROMPT_NEARER_SLICE, moduleEnabled::get, repeatEventHandler);
		bdvPromptsService.addListener(BdvPromptsBehaviours.REPEAT_PROMPT_FURTHER_SLICE, moduleEnabled::get, repeatEventHandler);

		// --- a "module" that works on contrast-adjusted pixels
		bdvPromptsService.addListener(BdvPromptsBehaviours.PROMPT_CONTRAST, e -> {
			final boolean isNewView = convertedViewsService.hasChangedSince(myChangeCounters[1]);
			if (isNewView) {
				myChangeCounters[1] = convertedViewsService.getChangeCounter();
				CapturedView<FloatType> view = convertedViewsService.getCurrentConvertedView(new FloatType());
				report("converted ", e, view, isNewView);
				ImageJFunctions.show(view.getImage(), "converted");
			} else {
				System.out.println("No new converted view");
			}
		});

		// --- J and K: a planner makes a plan (a list of events), the plan items go to an ordinary listener
		final Source<UnsignedShortType> spimSource = bdvStackSource.getSources().get(0).getSpimSource();
		final AtomicBoolean isPlanRunning = new AtomicBoolean(false);

		//the consumer of the plan items, fetching (or re-using) the view image per item
		final AtomicReference<BdvPromptsEvent> lastEvent = new AtomicReference<>();
		final AtomicReference<CapturedView<FloatType>> lastEventCapturedView = new AtomicReference<>();

		final BdvPromptsListener planItemConsumer = item -> {
			final boolean isNewView = !item.hasSameViewAs(lastEvent.get());
			if (isNewView) lastEventCapturedView.set(originalViewsService.getEventView(spimSource, new FloatType(), item, Interpolation.NLINEAR));
			lastEvent.set(item);
			report("plan item", item, lastEventCapturedView.get(), isNewView);
		};

		final SeedsPlanner.SeedsFinder seedsFinder = SeedsPlanner.contrastThresholdingSeeds(
				convertedViewsService.getConverterSetup(), SeedsUtils.giveBitFlagForMildDebug());
		//
		bdvPromptsService.addListener(BdvPromptsBehaviours.MULTI_PROMPT, () -> !isPlanRunning.get(), e -> {
			final CapturedView<FloatType> view = originalViewsService.getEventView(spimSource, new FloatType(), e, Interpolation.NLINEAR);
			final List<BdvPromptsEvent> plan = SeedsPlanner.plan(e, view.getImage(), seedsFinder);
			System.out.println("J: " + plan.size() + " seed(s) found");
			reviewThenRun(plan, "J: prompts at the found seeds", bdvPromptsService, planItemConsumer, isPlanRunning);
		});


		final TrackingPlanner.LabelPresenceIndicator brightPixels = new BrightPixelsIndicator(img, 500);
		//
		bdvPromptsService.addListener(BdvPromptsBehaviours.TRACKING_PROMPT, () -> !isPlanRunning.get(), e -> {
			final List<BdvPromptsEvent> plan = TrackingPlanner.plan(e, brightPixels, 1.0, 1000);
			System.out.println("K: object found in " + plan.size() + " slice(s)");
			reviewThenRun(plan, "K: prompts following the object", bdvPromptsService, planItemConsumer, isPlanRunning);
		});

		// --- 'D' toggles the guard of the "L" module
		final Behaviours b = new Behaviours(new InputTriggerConfig(), "bdv");
		b.install(bdv.getTriggerbindings(), "demo");
		b.behaviour((ClickBehaviour) (x, y) -> {
			moduleEnabled.set(!moduleEnabled.get());
			bdv.getViewerPanel().showMessage("'L' module enabled: " + moduleEnabled.get());
		}, "toggle L module", "D");
	}

	/**
	 * The plan is first shown in the {@link PlanReviewer}, and executed only if the user chooses "Proceed".
	 * NB: the dialog is opened only after the current event (the end of the user's drag) has been
	 * fully processed, and the guard 'isPlanRunning' is raised meanwhile, so no other plan starts.
	 */
	static void reviewThenRun(final List<BdvPromptsEvent> plan, final String title,
	                          final BdvPromptsService service, final BdvPromptsListener consumer,
	                          final AtomicBoolean isPlanRunning) {
		if (plan.isEmpty()) return;
		isPlanRunning.set(true);
		SwingUtilities.invokeLater(() -> {
			if (PlanReviewer.review(plan, service, title)) runPlan(plan, service, consumer, isPlanRunning);
			else isPlanRunning.set(false);
		});
	}

	/** The client's own "executor": off the EDT, so that the BDV shows every step. */
	static void runPlan(final List<BdvPromptsEvent> plan, final BdvPromptsService service,
	                    final BdvPromptsListener consumer, final AtomicBoolean isPlanRunning) {
		if (plan.isEmpty()) return;
		isPlanRunning.set(true);
		new Thread(() -> {
			try {
				for (BdvPromptsEvent item : plan) {
					PlanUtils.positionAndShowPromptAccordingToEvent(service, item);
					consumer.onPromptEntered(item);
					Thread.sleep(300); //just for the demo, to be able to follow it
				}
			} catch (InterruptedException e) {
				//just stop
			} finally {
				service.hideBox();
				isPlanRunning.set(false);
			}
		}, "demo plan runner").start();
	}

	/**
	 * Demo stand-in for, e.g., a Labkit labeling: an object is "present" where the image is bright.
	 * NB: the demo image is shown with the identity source transform, so global = pixel coordinates.
	 */
	static class BrightPixelsIndicator implements TrackingPlanner.LabelPresenceIndicator {
		BrightPixelsIndicator(final Img<UnsignedShortType> img, final int threshold) {
			this.img = img;
			this.threshold = threshold;
		}

		private final Img<UnsignedShortType> img;
		private final int threshold;
		private RandomAccess<UnsignedShortType> ra;

		@Override
		public void prepareForQueryingSession() {
			ra = Views.extendZero(img).randomAccess();
		}

		@Override
		public boolean isPresent(final RealLocalizable globalPosition) {
			for (int d = 0; d < 3; ++d) ra.setPosition(Math.round(globalPosition.getDoublePosition(d)), d);
			return ra.get().get() > threshold;
		}
	}

	static void report(final String what, final BdvPromptsEvent e,
	                   final CapturedView<FloatType> view, final boolean isNewView) {
		double sum = 0;
		for (FloatType px : Views.interval(view.getImage(), e.asInterval())) sum += px.getRealDouble();
		System.out.printf("%s: %s, %s view image (level %d), mean in box = %.4f%n",
				what, e, isNewView ? "NEW" : "cached", view.getMipmapLevel(),
				sum / ((double) e.getWidth() * e.getHeight()));
	}
}
