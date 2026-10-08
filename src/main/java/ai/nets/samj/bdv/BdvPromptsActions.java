package ai.nets.samj.bdv;

import bdv.KeyConfigScopes;
import bdv.interactive.services.BdvPromptsService;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;

/**
 * The single place where BDV's rectangular prompting-relevant actions are named,
 * given their default triggers and their descriptions.
 * <p>
 * The {@link Descriptions} provider is what makes them listed in the BDV keymap
 * editor: BDV's {@code KeymapManager} harvests all {@link CommandDescriptionProvider}s
 * of the scope {@link KeyConfigScopes#BIGDATAVIEWER} found on the classpath (through
 * the SciJava plugin index, which is generated at compile time from the
 * {@link Plugin} annotation; no {@code Context} is needed in our code).
 * <p>
 * The {@link BdvPromptsService} itself works without this provider, its actions
 * would just not appear in the editor.
 */
public class BdvPromptsActions {

	public static final String PROMPT = "rectangle prompt on original view";
	public static final String[] PROMPT_KEYS = new String[] { "L" };

	public static final String PROMPT_CONTRAST = "rectangle prompt on contrast-adjusted view";
	public static final String[] PROMPT_CONTRAST_KEYS = new String[] { "shift L" };

	public static final String MULTI_PROMPT = "rectangle multi-prompt on original view";
	public static final String[] MULTI_PROMPT_KEYS = new String[] { "J" };

	/** Adds all the above actions, with their default triggers, to the service. */
	public static void addAllTo(final BdvPromptsService service) {
		service.addInsertPromptAction(PROMPT, PROMPT_KEYS);
		service.addInsertPromptAction(PROMPT_CONTRAST, PROMPT_CONTRAST_KEYS);
		service.addInsertPromptAction(MULTI_PROMPT, MULTI_PROMPT_KEYS);
	}

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
			descriptions.add(MULTI_PROMPT, MULTI_PROMPT_KEYS,
					  "Drag a box in which seeds are detected and processed iteratively as separate prompts.");
		}
	}
}
