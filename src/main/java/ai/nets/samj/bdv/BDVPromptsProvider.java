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
		this.bdv = new SAMJ_BDV<>(image);
	}

	final private SAMJ_BDV<T> bdv;
	private final Img<T> image;
	private final SAMJLogger logger;

	@Override
	public void setRectIconConsumer(BooleanConsumer consumer) {
		logger.warn("setRectIconConsumer");
	}

	@Override
	public void setPointsIconConsumer(BooleanConsumer consumer) {
		logger.warn("setPointsIconConsumer");
	}

	@Override
	public void setFreelineIconConsumer(BooleanConsumer consumer) {
		logger.warn("setFreelineIconConsumer");
	}

	@Override
	public RandomAccessibleInterval<?> giveProcessedSubImage(SAMModel selectedModel) {
		logger.warn("giveProcessedSubImage");
		return null;
	}

	@Override
	public void switchToThisNet(SAMModel promptsToNetAdapter) {
		logger.warn("switchToThisNet");
	}

	@Override
	public void notifyNetToClose() {
		logger.warn("notifyNetToClose");
	}

	@Override
	public List<Polygon> getPolygonsFromRoiManager() {
		logger.warn("getPolygonsFromRoiManager");
		return null;
	}

	@Override
	public void exportImageLabeling() {
		logger.warn("exportImageLabeling");
	}

	@Override
	public void improveExistingMask(File mask) {
		logger.warn("improveExistingMask");
	}

	@Override
	public void enableAddingToRoiManager(boolean shouldBeAdding) {
		logger.warn("enableAddingToRoiManager");
	}

	@Override
	public boolean isAddingToRoiManager() {
		logger.warn("isAddingToRoiManager");
		return true;
	}

	@Override
	public void switchToUsingRectangles() {
		logger.warn("switchToUsingRectangles");
	}

	@Override
	public void switchToUsingBrush() {
		logger.warn("switchToUsingBrush");
	}

	@Override
	public void switchToUsingPoints() {
		logger.warn("switchToUsingPoints");
	}

	@Override
	public void switchToNone() {
		logger.warn("switchToNone");
	}

	@Override
	public Object getFocusedImage() {
		logger.warn("getFocusedImage");
		return null;
	}

	@Override
	public void notifyException(SAMJException type, Exception ex) {
		logger.warn("notifyException");
	}

	@Override
	public SAMModel getNetBeingUsed() {
		logger.warn("getNetBeingUsed");
		return null;
	}
}
