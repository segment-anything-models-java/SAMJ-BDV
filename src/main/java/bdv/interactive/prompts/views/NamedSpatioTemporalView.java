package bdv.interactive.prompts.views;

import bdv.viewer.ViewerPanel;

public class NamedSpatioTemporalView extends SpatioTemporalView {
	public NamedSpatioTemporalView(ViewerPanel bdvViewerPanel, String name) {
		super(bdvViewerPanel);
		this.name = name;
	}

	private final String name;

	public String getName() {
		return name;
	}
}