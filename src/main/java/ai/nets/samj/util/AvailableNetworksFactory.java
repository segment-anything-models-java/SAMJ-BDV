package ai.nets.samj.util;

import ai.nets.samj.bdv.promptresponders.SamjResponder;
import ai.nets.samj.communication.model.SAMModel;
import ai.nets.samj.communication.model.SAMModels;
import bdv.interactive.prompts.BdvPrompts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class AvailableNetworksFactory {
	private final List<String> netNames = new ArrayList<>(10);
	private final List<SAMModel> netObjs = new ArrayList<>(10);

	public AvailableNetworksFactory() {
		try {
			new SAMModels().forEach(m -> {
				if (m.getInstallationManger().checkEverythingInstalled()) {
					netNames.add(m.getName());
					netObjs.add(m);
				}
			});
		} catch (Exception e) {
			System.out.println("Error querying SAMModels: "+e.getMessage());
		}
	}

	public List<String> availableModels() {
		return Collections.unmodifiableList(netNames);
	}

	public SAMModel getModel(final String ofThisNetName) {
		for (int i = 0; i < netNames.size(); ++i) {
			if (netNames.get(i).equals(ofThisNetName)) return netObjs.get(i);
		}
		return null;
	}

	public static SAMModel reportAndChooseFirstAvailable(final BdvPrompts<?,?> samj,
	                                                     Consumer<String> consoleReporter) {
		final AvailableNetworksFactory netFactory = new AvailableNetworksFactory();
		final List<String> availableModels = netFactory.availableModels();
		if (!availableModels.isEmpty()) {
			//grab the first available network
			SAMModel net = netFactory.getModel(availableModels.get(0));
			samj.addPromptsProcessor(new SamjResponder<>(net));
			availableModels.forEach(netName -> consoleReporter.accept("Detected available SAMJ: "+netName));
			return net;
		} else {
			return null;
		}
	}
}
