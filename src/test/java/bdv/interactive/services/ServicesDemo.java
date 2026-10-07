package bdv.interactive.services;

import ai.nets.samj.bdv.BdvPromptsActions;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvStackSource;
import bdv.interactive.services.OriginalViewsService.CapturedView;
import ij.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shows how the services are meant to be wired together; mimics the old
 * "L" (original pixels) and "shift L" (contrast-adjusted pixels) prompts.
 * <ul>
 *   <li>hold L and move the mouse to drag a box, release L to finish it</li>
 *   <li>hold shift+L for the same on contrast-adjusted pixels</li>
 *   <li>ctrl+COMMA (BDV preferences, "Keymap" page) lists the actions in the keymap editor, where they can be re-mapped</li>
 *   <li>press D to toggle the guard of the "L" listener (simulating a GUI checkbox)</li>
 *   <li>ctrl I / ctrl J rotate to front / side view, ctrl K rotates back; ctrl N / ctrl M move one slice closer / further</li>
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

		// --- extra 3D navigation: ctrl I/J/K side views, ctrl N/M slicing (also keymap-driven)
		new ViewNavigationBehaviours(bdv).addAllActions();

		// --- named actions; triggers come from BDV's keymap (defaults if unknown there)
		BdvPromptsActions.addAllTo(bdvPromptsService);
		bdvPromptsService.setBoxColor(BdvPromptsActions.PROMPT_CONTRAST, Color.MAGENTA);
		bdvPromptsService.setBoxStyle(BdvPromptsActions.PROMPT, BdvPromptsService.LineStyle.DASHED, 2.0f);

		// --- a "module" that works on original pixels, and caches its view image
		final AtomicBoolean moduleEnabled = new AtomicBoolean(true);
		//NB: -1 is never a valid counter value, so the very first prompt always captures a view
		final long[] myChangeCounters = new long[] {-1, -1};
		bdvPromptsService.addListener(BdvPromptsActions.PROMPT, moduleEnabled::get, e -> {
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

		// --- a "module" that works on contrast-adjusted pixels
		bdvPromptsService.addListener(BdvPromptsActions.PROMPT_CONTRAST, e -> {
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

		// --- 'D' toggles the guard of the "L" module
		final Behaviours b = new Behaviours(new InputTriggerConfig(), "bdv");
		b.install(bdv.getTriggerbindings(), "demo");
		b.behaviour((ClickBehaviour) (x, y) -> {
			moduleEnabled.set(!moduleEnabled.get());
			bdv.getViewerPanel().showMessage("'L' module enabled: " + moduleEnabled.get());
		}, "toggle L module", "D");
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
