package ai.nets.samj.bdv;

import bdv.KeyConfigScopes;
import bdv.interactive.services.RubberBandService;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;

/**
 * The single place where SAMJ-BDV's rubber-band actions are named, given their
 * default triggers and their descriptions.
 * <p>
 * The {@link Descriptions} provider is what makes them listed in the BDV keymap
 * editor: BDV's {@code KeymapManager} harvests all {@link CommandDescriptionProvider}s
 * of the scope {@link KeyConfigScopes#BIGDATAVIEWER} found on the classpath (through
 * the SciJava plugin index, which is generated at compile time from the
 * {@link Plugin} annotation; no {@code Context} is needed in our code).
 * <p>
 * The {@link RubberBandService} itself works without this provider, its actions
 * would just not appear in the editor.
 */
public class SamjBdvActions {

	public static final String PROMPT = "samj rubberband prompt";
	public static final String[] PROMPT_KEYS = new String[] { "L" };

	public static final String PROMPT_CONTRAST = "samj rubberband prompt on contrast-adjusted view";
	public static final String[] PROMPT_CONTRAST_KEYS = new String[] { "shift L" };

	public static final String MULTI_PROMPT = "samj rubberband multi-prompt";
	public static final String[] MULTI_PROMPT_KEYS = new String[] { "J" };

	/** Adds all the above actions, with their default triggers, to the service. */
	public static void addAllTo(final RubberBandService rubberBand) {
		rubberBand.addAction(PROMPT, PROMPT_KEYS);
		rubberBand.addAction(PROMPT_CONTRAST, PROMPT_CONTRAST_KEYS);
		rubberBand.addAction(MULTI_PROMPT, MULTI_PROMPT_KEYS);
	}

	@Plugin(type = CommandDescriptionProvider.class)
	public static class Descriptions extends CommandDescriptionProvider {
		public Descriptions() {
			super(KeyConfigScopes.BIGDATAVIEWER, RubberBandService.KEYCONFIG_CONTEXT);
		}

		@Override
		public void getCommandDescriptions(final CommandDescriptions descriptions) {
			descriptions.add(PROMPT, PROMPT_KEYS,
					"Drag a box (hold the key and move the mouse) to segment the object in it with SAMJ.");
			descriptions.add(PROMPT_CONTRAST, PROMPT_CONTRAST_KEYS,
					"Like the SAMJ prompt, but SAMJ sees the image with the current brightness/contrast setting applied.");
			descriptions.add(MULTI_PROMPT, MULTI_PROMPT_KEYS,
					"Drag a box in which seeds are detected; each seed is then submitted to SAMJ as a separate prompt.");
		}
	}
}
