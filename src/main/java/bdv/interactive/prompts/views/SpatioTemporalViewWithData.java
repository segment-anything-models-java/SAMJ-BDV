package bdv.interactive.prompts.views;

import bdv.util.BdvHandle;

/**
 * Exactly the same class and same purpose as {@link SpatioTemporalView},
 * with the ability to attach user data (under the type 'D'), different
 * data for each view instance (the data need not be the same even when
 * the spatiotemporal view is the same).
 * A copy of the user data is **NOT** created.
 * @param <D> Type for user data that would be attached per every view.
 */
public class SpatioTemporalViewWithData<D> extends SpatioTemporalView {
	public SpatioTemporalViewWithData(BdvHandle bdv, D userData) {
		super(bdv);
		this.userData = userData;
	}

	private final D userData;

	public D getUserData() {
		return this.userData;
	}
}
