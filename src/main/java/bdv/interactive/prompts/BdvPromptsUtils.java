package bdv.interactive.prompts;

import ai.nets.samj.bdv.promptresponders.FakeResponder;
import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.bdv.promptresponders.ShowImageInIJResponder;
import ai.nets.samj.communication.model.SAMModel;
import ai.nets.samj.util.AvailableNetworksFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

public class BdvPromptsUtils {
	public static
	SAMModel getNetWorkModel(final String networkName) {
		return new AvailableNetworksFactory().getModel(networkName);
	}


	/**
	 * At first, it removes any network from the list of prompters in the object (bdvPrompter).
	 * It removes even model that it is now asked to be switched to (model) -- this is merely
	 * a "resetting" of a model. Finally, it installs the requested model into the bdvPrompter..
	 *
	 * If model == null, then {@link FakeResponder} is added. Since this particular one is not
	 * a proper {@link SamjResponder}, null is returned instead. Normally, however, a SamjResponder
	 * wrapped around the asked model is returned.
	 */
	public static
	<OT extends RealType<OT> & NativeType<OT>>
	SamjResponder<OT> switchToThisNetwork(final SAMModel model, final BdvPrompts<?,OT> bdvPrompter) {
		BdvPrompts.PromptsProcessor<OT> p = findAnySamNetworkOrNull(bdvPrompter);
		while (p != null) {
			bdvPrompter.removePromptsProcessor(p);
			p = findAnySamNetworkOrNull(bdvPrompter);
		}

		//now, add the given one
		SamjResponder<OT> samj = null;
		if (model != null) {
			samj = new SamjResponder<>(model);
			bdvPrompter.addPromptsProcessor(samj);
		} else {
			bdvPrompter.addPromptsProcessor( new FakeResponder<>() );
		}
		return samj;
	}


	public static
	<OT extends RealType<OT> & NativeType<OT>>
	BdvPrompts.PromptsProcessor<OT> findAnySamNetworkOrNull(final BdvPrompts<?,OT> bdvPrompter) {
		for (BdvPrompts.PromptsProcessor<OT> p : bdvPrompter.listPromptsProcessors()) {
			if (p instanceof SamjResponder) return p;
		}
		return null;
	}


	public static
	<OT extends RealType<OT> & NativeType<OT>>
	ShowImageInIJResponder<OT> findShowImageInIjResponderOrNull(final BdvPrompts<?,OT> bdvPrompter) {
		for (BdvPrompts.PromptsProcessor<OT> p : bdvPrompter.listPromptsProcessors()) {
			if (p instanceof ShowImageInIJResponder) return (ShowImageInIJResponder<OT>)p;
		}
		return null;
	}
}
