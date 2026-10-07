package bdv.interactive.services;

/**
 * Notified by the {@link BdvPromptsService} when the user has finished
 * dragging a prompt box with a trigger this listener was registered for.
 * <p>
 * The notification is delivered on the thread that delivered the closing
 * input event, which is normally the AWT Event Dispatch Thread. Any heavy
 * work (e.g. running a network) should therefore be handed over to another
 * thread, otherwise the whole viewer freezes.
 */
@FunctionalInterface
public interface BdvPromptsListener {
	void onPromptEntered(final BdvPromptsEvent event);
}
