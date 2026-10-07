package bdv.interactive.services;

import ai.nets.samj.bdv.BdvPromptsActions;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvStackSource;
import bdv.interactive.services.OriginalViewsService.CapturedView;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
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
 * </ul>
 */
public class ServicesDemo {
	public static void main(String[] args) {
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
		final OriginalViewsService originalViews = new OriginalViewsService(bdv);
		final ConvertedViewsService convertedViews = new ConvertedViewsService(bdv, bdvStackSource.getSources().get(0));

		// --- named actions; triggers come from BDV's keymap (defaults if unknown there)
		BdvPromptsActions.addAllTo(bdvPromptsService);
		bdvPromptsService.setBoxColor(BdvPromptsActions.PROMPT_CONTRAST, Color.MAGENTA);
		bdvPromptsService.setBoxStyle(BdvPromptsActions.PROMPT, BdvPromptsService.LineStyle.DASHED, 2.0f);

		// --- a "module" that works on original pixels, and caches its view image
		final AtomicBoolean moduleEnabled = new AtomicBoolean(true);
		final Object[] cache = new Object[1]; //poor man's field
		bdvPromptsService.addListener(BdvPromptsActions.PROMPT, moduleEnabled::get, e -> {
			@SuppressWarnings("unchecked")
			CapturedView<FloatType> view = (CapturedView<FloatType>) cache[0];
			final boolean isNewView = view == null || originalViews.hasChangedSince(view.getChangeCounter());
			if (isNewView) {
				view = originalViews.getCurrentView(bdvStackSource.getSources().get(0).getSpimSource(), new FloatType());
				cache[0] = view;
			}
			report("original ", e, view, isNewView);
		});

		// --- a "module" that works on contrast-adjusted pixels
		final Object[] cache2 = new Object[1];
		bdvPromptsService.addListener(BdvPromptsActions.PROMPT_CONTRAST, e -> {
			@SuppressWarnings("unchecked")
			CapturedView<FloatType> view = (CapturedView<FloatType>) cache2[0];
			final boolean isNewView = view == null || convertedViews.hasChangedSince(view.getChangeCounter());
			if (isNewView) {
				view = convertedViews.getCurrentConvertedView(new FloatType());
				cache2[0] = view;
			}
			report("converted", e, view, isNewView);
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
		System.out.printf("%s: %s, %s view image (counter %d, level %d), mean in box = %.4f%n",
				what, e, isNewView ? "NEW" : "cached", view.getChangeCounter(), view.getMipmapLevel(),
				sum / ((double) e.getWidth() * e.getHeight()));
	}
}
