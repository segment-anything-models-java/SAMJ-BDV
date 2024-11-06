package ai.nets.samj.util;

import ai.nets.samj.communication.model.SAMModel;
import ai.nets.samj.communication.model.SAMModels;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
		} catch (IOException | InterruptedException e) {
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
}
