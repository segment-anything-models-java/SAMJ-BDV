package ai.nets.samj.bdv.views;

import bdv.util.BdvHandle;

public class NamedSpatioTemporalView extends SpatioTemporalView {
	public NamedSpatioTemporalView(BdvHandle bdv, String name) {
		super(bdv);
		this.name = name;
	}

	private final String name;

	public String getName() {
		return name;
	}
}