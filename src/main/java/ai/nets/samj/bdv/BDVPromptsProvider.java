/*-
 * #%L
 * Plugin to help image annotation with SAM-based Deep Learning models
 * %%
 * Copyright (C) 2024 SAMJ developers.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ai.nets.samj.bdv;

import ai.nets.samj.communication.model.SAMModel;
import ai.nets.samj.ui.PromptsResultsDisplay;
import ai.nets.samj.ui.SAMJLogger;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.img.Img;

import java.io.File;
import java.awt.Polygon;
import java.util.List;

public class BDVPromptsProvider <T extends RealType<T>> implements PromptsResultsDisplay {
	public BDVPromptsProvider(final Img<T> image, final SAMJLogger logger) {
		this.image = image;
		this.logger = logger;
	}
	private final Img<T> image;
	private final SAMJLogger logger;

	@Override
	public void setRectIconConsumer(BooleanConsumer consumer) {
		logger.warn("foo");
	}

	@Override
	public void setPointsIconConsumer(BooleanConsumer consumer) {

	}

	@Override
	public void setFreelineIconConsumer(BooleanConsumer consumer) {

	}

	@Override
	public RandomAccessibleInterval<?> giveProcessedSubImage(SAMModel selectedModel) {
		return null;
	}

	@Override
	public void switchToThisNet(SAMModel promptsToNetAdapter) {

	}

	@Override
	public void notifyNetToClose() {

	}

	@Override
	public List<Polygon> getPolygonsFromRoiManager() {
		return null;
	}

	@Override
	public void exportImageLabeling() {

	}

	@Override
	public void improveExistingMask(File mask) {

	}

	@Override
	public void enableAddingToRoiManager(boolean shouldBeAdding) {

	}

	@Override
	public boolean isAddingToRoiManager() {
		return false;
	}

	@Override
	public void switchToUsingRectangles() {

	}

	@Override
	public void switchToUsingBrush() {

	}

	@Override
	public void switchToUsingPoints() {

	}

	@Override
	public void switchToNone() {

	}

	@Override
	public Object getFocusedImage() {
		return null;
	}

	@Override
	public void notifyException(SAMJException type, Exception ex) {

	}

	@Override
	public SAMModel getNetBeingUsed() {
		return null;
	}
}
